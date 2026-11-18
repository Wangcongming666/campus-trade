package com.campus.trade.controller;

import com.campus.trade.common.PageQuery;
import com.campus.trade.common.PageResult;
import com.campus.trade.common.Result;
import com.campus.trade.dto.InterestUpdateDTO;
import com.campus.trade.dto.PasswordUpdateDTO;
import com.campus.trade.dto.StudentVerifyDTO;
import com.campus.trade.dto.UserUpdateDTO;
import com.campus.trade.service.GoodsService;
import com.campus.trade.service.UserInterestService;
import com.campus.trade.service.UserService;
import com.campus.trade.vo.CategoryVO;
import com.campus.trade.vo.GoodsVO;
import com.campus.trade.vo.UserMeVO;
import com.campus.trade.vo.UserPublicVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "用户")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final GoodsService goodsService;
    private final UserInterestService interestService;

    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public Result<UserMeVO> me() {
        return Result.ok(userService.me());
    }

    @Operation(summary = "修改个人资料（nickname/avatar/phone，传哪个改哪个）")
    @PutMapping("/me")
    public Result<UserMeVO> updateMe(@Valid @RequestBody UserUpdateDTO dto) {
        return Result.ok(userService.updateMe(dto));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/me/password")
    public Result<Void> updatePassword(@Valid @RequestBody PasswordUpdateDTO dto) {
        userService.updatePassword(dto);
        return Result.ok();
    }

    @Operation(summary = "提交学生认证")
    @PostMapping("/me/student-verify")
    public Result<Map<String, Object>> studentVerify(@Valid @RequestBody StudentVerifyDTO dto) {
        return Result.ok(userService.submitStudentVerify(dto));
    }

    @Operation(summary = "我的发布（可按 status 过滤）")
    @GetMapping("/me/goods")
    public Result<PageResult<GoodsVO>> myGoods(@RequestParam(required = false)
                                               @Pattern(regexp = "PENDING_AUDIT|ON_SHELF|RESERVED|OFF_SHELF|SOLD|REJECTED",
                                                       message = "status 取值不合法") String status,
                                               @ParameterObject PageQuery pq) {
        return Result.ok(goodsService.myGoods(status, pq));
    }

    @Operation(summary = "我的收藏")
    @GetMapping("/me/favorites")
    public Result<PageResult<GoodsVO>> myFavorites(@ParameterObject PageQuery pq) {
        return Result.ok(goodsService.myFavorites(pq));
    }

    @Operation(summary = "我的兴趣分类")
    @GetMapping("/me/interests")
    public Result<List<CategoryVO>> interests() {
        return Result.ok(interestService.list());
    }

    @Operation(summary = "设置兴趣分类（整体覆盖，最多 5 个，空数组清空）")
    @PutMapping("/me/interests")
    public Result<List<CategoryVO>> updateInterests(@Valid @RequestBody InterestUpdateDTO dto) {
        return Result.ok(interestService.replace(dto.getCategoryIds()));
    }

    @Operation(summary = "用户公开主页")
    @GetMapping("/{id}")
    public Result<UserPublicVO> publicProfile(@PathVariable Long id) {
        return Result.ok(userService.publicProfile(id));
    }
}
