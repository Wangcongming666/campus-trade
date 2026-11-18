#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
【模拟数据】校园二手交易行为数据生成脚本（数据集B）
注意：本脚本生成的是 *模拟数据*，仅用于离线算法对比实验，不代表任何真实用户行为。

规模：约 2000 用户、5000 商品、20 个分类；固定随机种子保证可复现。
生成规则：
  1. 分类热度服从 Zipf(0.8)；商品数按分类热度分配；
  2. 商品热度服从对数正态分布（长尾）；每个商品属于分类下 5 个"子主题"之一（如教材的不同专业）；
  3. 每个用户注册时选择 1~3 个兴趣分类，并在每个兴趣分类内偏好 1 个子主题；
  4. 用户活跃度长尾：约 15% 新用户只有 1~4 次交互，其余活跃度服从对数正态；
  5. 每次交互：75% 概率落在兴趣分类内（其中 60% 落在偏好子主题），否则按全局分类热度随机；
     选中分类后按商品热度抽商品；先产生一条"浏览"，再以一定概率产生收藏/聊天/下单，
     深度行为概率随"与用户偏好的匹配程度"升高（随手浏览 < 兴趣分类 < 兴趣分类+偏好子主题），
     模拟"越喜欢越可能收藏/聊天/下单"的真实规律。
  6. 价格字段（定价建议实验用）使用独立随机流 RandomState(SEED+1)，在行为生成之后才抽取，
     因此新增价格字段不会改变 user_behavior.csv / users.csv（推荐实验结果保持不变）：
       原价  o = 分类基准价 × 1.6 × 子主题价位系数(对数正态 σ=0.45) × 对数正态(σ=0.35)，约 35% 商品未填原价；
       成色  NEW/LIKE_NEW/GOOD/FAIR/POOR 概率 0.12/0.28/0.32/0.18/0.10；
       真实残值 v = o × 真实成色系数(0.88/0.74/0.60/0.46/0.32，故意与系统默认系数不同) × 分类折旧系数 U(0.8,1.1) × 对数正态(σ=0.18)；
       挂牌价 = min(o, v × 对数正态(μ=0.08, σ=0.10))（卖家习惯略高报价），其中 2% 为录入错误（×10 或 ×0.1）；
       成交价（仅已售商品）= 挂牌价(无录入错误) × U(0.85, 1.0)，其中 2% 为打包价/近乎赠送（×3 或 ×0.15）。
输出（UTF-8 CSV，列名与 user_behavior / goods 表一致）：
  data/campus/users.csv          user_id,interest_categories(竖线分隔)
  data/campus/goods.csv          id,title,category_id,price,status,create_time,original_price,condition,deal_price
                                 （price=挂牌价；original_price 为空表示未填写；deal_price 仅已售商品有值，模拟 trade_order.price）
  data/campus/user_behavior.csv  id,user_id,goods_id,behavior_type,create_time
behavior_type：1 浏览、2 收藏、3 聊天、4 下单
"""
import csv, os, sys
from datetime import datetime, timedelta
import numpy as np

SEED = 20261118
N_USERS, N_GOODS, N_CATS, N_SUB = 2000, 5000, 20, 5
END = datetime(2026, 11, 1, 0, 0, 0)
DAYS = 180

CATS = [
    ("教材书籍", ["高等数学", "线性代数", "大学英语", "C语言程序设计", "数据结构", "大学物理", "教材", "习题册", "考研真题", "专业课"]),
    ("手机数码", ["iPhone", "华为", "小米", "OPPO", "vivo", "手机", "平板", "耳机", "充电宝", "智能手表"]),
    ("电脑配件", ["笔记本", "机械键盘", "鼠标", "显示器", "U盘", "移动硬盘", "内存条", "散热器", "联想", "拓展坞"]),
    ("生活电器", ["吹风机", "电饭煲", "台灯", "小风扇", "电热水壶", "加湿器", "插线板", "暖手宝", "剃须刀", "电煮锅"]),
    ("自行车", ["山地车", "公路车", "折叠车", "捷安特", "美利达", "电动车", "车锁", "头盔", "代步车", "单车"]),
    ("运动健身", ["羽毛球拍", "篮球", "足球", "瑜伽垫", "哑铃", "跳绳", "网球拍", "护膝", "李宁", "运动鞋"]),
    ("服饰鞋包", ["羽绒服", "卫衣", "牛仔裤", "连衣裙", "帆布鞋", "双肩包", "外套", "衬衫", "耐克", "阿迪达斯"]),
    ("美妆个护", ["口红", "粉底液", "面膜", "香水", "洗面奶", "防晒霜", "眼影", "护肤套装", "卸妆水", "精华"]),
    ("宿舍用品", ["床帘", "收纳箱", "衣架", "床上桌", "凉席", "被子", "枕头", "挂钩", "镜子", "置物架"]),
    ("乐器", ["吉他", "尤克里里", "电子琴", "口琴", "小提琴", "架子鼓", "笛子", "调音器", "琴包", "变调夹"]),
    ("考研资料", ["考研数学", "考研英语", "考研政治", "肖秀荣", "张宇", "汤家凤", "真题", "笔记", "网课资料", "背诵手册"]),
    ("游戏娱乐", ["Switch", "PS5", "游戏卡带", "手柄", "桌游", "狼人杀", "乐高", "拼图", "Steam", "卡牌"]),
    ("票券卡券", ["电影票", "演唱会门票", "健身卡", "话费卡", "优惠券", "会员", "游泳卡", "车票", "景区门票", "饭卡"]),
    ("文具办公", ["钢笔", "笔记本子", "计算器", "打印机", "订书机", "荧光笔", "文件夹", "便利贴", "书立", "台历"]),
    ("摄影器材", ["相机", "佳能", "尼康", "索尼", "镜头", "三脚架", "拍立得", "富士", "存储卡", "稳定器"]),
    ("箱包行李", ["行李箱", "旅行包", "登机箱", "收纳袋", "电脑包", "斜挎包", "手提包", "腰包", "拉杆箱", "背包"]),
    ("食品零食", ["零食大礼包", "咖啡", "茶叶", "坚果", "巧克力", "饼干", "牛奶", "方便面", "水果", "特产"]),
    ("家具收纳", ["椅子", "书架", "折叠桌", "懒人沙发", "鞋架", "储物柜", "地毯", "落地灯", "穿衣镜", "收纳盒"]),
    ("户外露营", ["帐篷", "睡袋", "登山杖", "冲锋衣", "露营灯", "防潮垫", "背包", "水壶", "登山鞋", "折叠椅"]),
    ("其他闲置", ["绿植", "花盆", "手工", "摆件", "海报", "明信片", "玩偶", "雨伞", "钥匙扣", "杂物"]),
]
CONDS = ["全新", "九成新", "八成新", "自用", "闲置", "毕业出", "低价出", "急出"]
# 深度行为概率 (收藏, 聊天, 下单)，按匹配程度 0/1/2
DEEP_P = {0: (0.08, 0.04, 0.01), 1: (0.18, 0.10, 0.04), 2: (0.30, 0.20, 0.08)}
COND_KEYS = ["NEW", "LIKE_NEW", "GOOD", "FAIR", "POOR"]
COND_P = [0.12, 0.28, 0.32, 0.18, 0.10]
TRUE_COND_F = [0.88, 0.74, 0.60, 0.46, 0.32]  # 模拟世界中的"真实"成色系数（算法不可见）
PRICE_BASE = [30, 1500, 300, 80, 400, 100, 120, 90, 40, 500, 40, 600, 50, 30, 2000, 150, 30, 150, 200, 20]


def main(out_dir):
    rng = np.random.RandomState(SEED)
    os.makedirs(out_dir, exist_ok=True)
    # 1. 分类热度 Zipf(0.8)，打乱后对应分类
    ranks = np.arange(1, N_CATS + 1)
    cat_w = 1.0 / ranks ** 0.8
    cat_w = cat_w[rng.permutation(N_CATS)]
    cat_w /= cat_w.sum()
    # 2. 商品：分类概率 = 0.7×分类热度 + 0.3×均匀（保证小类也有商品）
    cats = rng.choice(N_CATS, size=N_GOODS, p=0.7 * cat_w + 0.3 / N_CATS)
    subs = rng.randint(0, N_SUB, size=N_GOODS)
    pop = rng.lognormal(mean=0.0, sigma=1.3, size=N_GOODS)  # 长尾热度
    goods_create = [END - timedelta(days=float(rng.uniform(1, DAYS + 20))) for _ in range(N_GOODS)]
    goods_ids = np.arange(1, N_GOODS + 1) + 1_000_000  # 模拟 ID
    titles, prices, status = [], [], []
    for g in range(N_GOODS):
        c = cats[g]
        kws = CATS[c][1]
        # 同一子主题的商品共享主关键词，便于内容相似（TF-IDF）验证
        main_kw = kws[subs[g] * 2 % len(kws)]
        extra = kws[rng.randint(len(kws))]
        cond = CONDS[rng.randint(len(CONDS))]
        titles.append(f"{cond} {main_kw} {extra}" if extra != main_kw else f"{cond} {main_kw}")
        rng.lognormal(0, 0.5)  # v1 的价格抽样，保留调用以保持主随机流不变（价格改由独立随机流生成）
        prices.append(None)
        status.append(1)
    by_cat_sub = {}
    for g in range(N_GOODS):
        by_cat_sub.setdefault((cats[g], subs[g]), []).append(g)
    by_cat = {}
    for g in range(N_GOODS):
        by_cat.setdefault(cats[g], []).append(g)

    def pick(lst):
        w = pop[lst]
        return lst[rng.choice(len(lst), p=w / w.sum())]

    # 3. 用户兴趣
    users = []
    for u in range(N_USERS):
        k = rng.choice([1, 2, 3], p=[0.3, 0.45, 0.25])
        ic = list(rng.choice(N_CATS, size=k, replace=False, p=cat_w))
        pref_sub = {c: int(rng.randint(N_SUB)) for c in ic}
        if rng.rand() < 0.15:
            n_act = int(rng.randint(1, 5))  # 新用户
        else:
            n_act = int(min(300, max(5, rng.lognormal(mean=2.9, sigma=0.8))))
        users.append((u + 1, ic, pref_sub, n_act))

    # 4. 行为
    rows = []
    bid = 1
    sold = set()
    for uid, ic, pref_sub, n_act in users:
        for _ in range(n_act):
            # match: 0=随手浏览（非兴趣分类）, 1=兴趣分类, 2=兴趣分类且偏好子主题
            if rng.rand() < 0.75:
                c = ic[rng.randint(len(ic))]
                if rng.rand() < 0.6 and (c, pref_sub[c]) in by_cat_sub:
                    g = pick(by_cat_sub[(c, pref_sub[c])])
                    match = 2
                else:
                    g = pick(by_cat[c])
                    match = 1
            else:
                c = rng.choice(N_CATS, p=cat_w)
                g = pick(by_cat[c])
                match = 0
            start = max(goods_create[g], END - timedelta(days=DAYS))
            span = (END - start).total_seconds()
            t = start + timedelta(seconds=float(rng.uniform(0, span)))
            # 深度行为（收藏/聊天/下单）概率与匹配程度正相关：越符合偏好越可能产生深度行为
            p_fav, p_chat, p_order = DEEP_P[match]
            seq = [1]
            if rng.rand() < p_fav:
                seq.append(2)
            if rng.rand() < p_chat:
                seq.append(3)
            if rng.rand() < p_order and g not in sold:
                seq.append(4)
            for i, bt in enumerate(seq):
                tt = t + timedelta(minutes=5 * i)
                rows.append((bid, uid, int(goods_ids[g]), bt, tt.strftime("%Y-%m-%d %H:%M:%S")))
                bid += 1
                if bt == 4:
                    sold.add(g)
    for g in sold:
        status[g] = 2  # 已售出（离线实验不按状态过滤，仅供在线逻辑演示）

    # 5. 价格字段（独立随机流，不影响上面的行为数据）
    prng = np.random.RandomState(SEED + 1)
    sub_mult = prng.lognormal(0, 0.45, size=(N_CATS, N_SUB))
    cat_dep = prng.uniform(0.8, 1.1, size=N_CATS)
    orig_prices, conds, deal_prices = [], [], []
    for g in range(N_GOODS):
        c, sb = cats[g], subs[g]
        o = PRICE_BASE[c] * 1.6 * sub_mult[c, sb] * prng.lognormal(0, 0.35)
        o = max(5.0, round(o))
        k = prng.choice(5, p=COND_P)
        v = o * TRUE_COND_F[k] * cat_dep[c] * prng.lognormal(0, 0.18)
        listing = min(o, v * prng.lognormal(0.08, 0.10))
        clean_listing = listing
        r = prng.rand()
        if r < 0.01:
            listing *= 10
        elif r < 0.02:
            listing *= 0.1
        has_orig = prng.rand() >= 0.35
        deal = clean_listing * prng.uniform(0.85, 1.0)
        r2 = prng.rand()
        if r2 < 0.01:
            deal *= 3
        elif r2 < 0.02:
            deal *= 0.15
        prices[g] = round(float(max(1.0, listing)), 2)
        orig_prices.append(f"{o:.2f}" if has_orig else "")
        conds.append(COND_KEYS[k])
        deal_prices.append(f"{max(1.0, deal):.2f}" if g in sold else "")

    with open(os.path.join(out_dir, "users.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["user_id", "interest_categories"])
        for uid, ic, _, _ in users:
            w.writerow([uid, "|".join(str(int(c) + 1) for c in ic)])
    with open(os.path.join(out_dir, "goods.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["id", "title", "category_id", "price", "status", "create_time",
                    "original_price", "condition", "deal_price"])
        for g in range(N_GOODS):
            w.writerow([int(goods_ids[g]), titles[g], int(cats[g]) + 1, prices[g], status[g],
                        goods_create[g].strftime("%Y-%m-%d %H:%M:%S"), orig_prices[g], conds[g], deal_prices[g]])
    with open(os.path.join(out_dir, "user_behavior.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["id", "user_id", "goods_id", "behavior_type", "create_time"])
        w.writerows(rows)
    with open(os.path.join(out_dir, "categories.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["id", "name"])
        for i, (name, _) in enumerate(CATS):
            w.writerow([i + 1, name])
    types = np.bincount([r[3] for r in rows], minlength=5)
    print(f"[模拟数据] users={N_USERS} goods={N_GOODS} categories={N_CATS} behaviors={len(rows)} "
          f"browse={types[1]} favorite={types[2]} chat={types[3]} order={types[4]} seed={SEED} "
          f"sold_goods={len(sold)} with_original_price={sum(1 for x in orig_prices if x)}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "data", "campus"))
