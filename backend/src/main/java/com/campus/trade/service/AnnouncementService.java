package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trade.common.BizException;
import com.campus.trade.common.Constants.AnnouncementStatus;
import com.campus.trade.common.ErrorCode;
import com.campus.trade.common.PageQuery;
import com.campus.trade.common.PageResult;
import com.campus.trade.dto.AnnouncementSaveDTO;
import com.campus.trade.entity.Announcement;
import com.campus.trade.mapper.AnnouncementMapper;
import com.campus.trade.security.SecurityUtils;
import com.campus.trade.vo.AnnouncementVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;

/** 公告：前台只看 PUBLISHED；后台 CRUD + 状态 DRAFT / PUBLISHED / OFF_SHELF（发布时写 publish_time）。 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private static final Set<String> STATUSES = Set.of(AnnouncementStatus.DRAFT, AnnouncementStatus.PUBLISHED,
            AnnouncementStatus.OFF_SHELF);

    private final AnnouncementMapper mapper;

    public PageResult<AnnouncementVO> publicPage(PageQuery pq) {
        Page<Announcement> page = mapper.selectPage(pq.toPage(), new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, AnnouncementStatus.PUBLISHED)
                .orderByDesc(Announcement::getPublishTime).orderByDesc(Announcement::getId));
        return PageResult.of(page, AnnouncementVO::of);
    }

    public AnnouncementVO publicDetail(Long id) {
        Announcement a = mapper.selectById(id);
        if (a == null || !AnnouncementStatus.PUBLISHED.equals(a.getStatus())) {
            throw new BizException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
        }
        return AnnouncementVO.of(a);
    }

    public PageResult<AnnouncementVO> adminPage(String status, PageQuery pq) {
        Page<Announcement> page = mapper.selectPage(pq.toPage(), new LambdaQueryWrapper<Announcement>()
                .eq(StringUtils.hasText(status), Announcement::getStatus, status)
                .orderByDesc(Announcement::getCreateTime).orderByDesc(Announcement::getId));
        return PageResult.of(page, AnnouncementVO::of);
    }

    public AnnouncementVO adminDetail(Long id) {
        return AnnouncementVO.of(require(id));
    }

    public AnnouncementVO create(AnnouncementSaveDTO dto) {
        Announcement a = new Announcement();
        a.setTitle(dto.getTitle().trim());
        a.setContent(dto.getContent());
        a.setPublisherId(SecurityUtils.requireUserId());
        boolean publish = AnnouncementStatus.PUBLISHED.equals(dto.getStatus());
        a.setStatus(publish ? AnnouncementStatus.PUBLISHED : AnnouncementStatus.DRAFT);
        a.setPublishTime(publish ? LocalDateTime.now() : null);
        mapper.insert(a);
        return AnnouncementVO.of(mapper.selectById(a.getId()));
    }

    /** 修改标题/正文（不改状态）。 */
    public AnnouncementVO update(Long id, AnnouncementSaveDTO dto) {
        require(id);
        mapper.update(null, new LambdaUpdateWrapper<Announcement>().eq(Announcement::getId, id)
                .set(Announcement::getTitle, dto.getTitle().trim()).set(Announcement::getContent, dto.getContent()));
        return AnnouncementVO.of(mapper.selectById(id));
    }

    public AnnouncementVO changeStatus(Long id, String status) {
        if (!STATUSES.contains(status)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "status 取值为 DRAFT/PUBLISHED/OFF_SHELF");
        }
        Announcement cur = require(id);
        LambdaUpdateWrapper<Announcement> w = new LambdaUpdateWrapper<Announcement>().eq(Announcement::getId, id)
                .set(Announcement::getStatus, status);
        if (AnnouncementStatus.PUBLISHED.equals(status) && !AnnouncementStatus.PUBLISHED.equals(cur.getStatus())) {
            w.set(Announcement::getPublishTime, LocalDateTime.now())
                    .set(Announcement::getPublisherId, SecurityUtils.requireUserId());
        }
        mapper.update(null, w);
        return AnnouncementVO.of(mapper.selectById(id));
    }

    public void delete(Long id) {
        require(id);
        mapper.deleteById(id);
    }

    private Announcement require(Long id) {
        Announcement a = mapper.selectById(id);
        if (a == null) {
            throw new BizException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
        }
        return a;
    }
}
