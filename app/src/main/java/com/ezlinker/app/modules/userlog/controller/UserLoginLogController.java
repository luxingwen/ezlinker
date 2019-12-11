package com.ezlinker.app.modules.userlog.controller;


import com.ezlinker.app.common.AbstractXController;
import com.ezlinker.app.modules.userlog.model.UserLoginLog;
import com.ezlinker.app.modules.userlog.service.IUserLoginLogService;
import com.ezlinker.common.exception.XException;
import com.ezlinker.common.exchange.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * <p>
 * 用户登录日志 前端控制器
 * </p>
 *
 * @author wangwenhai
 * @since 2019-11-12
 */
@RestController
@RequestMapping("/userLogs")
public class UserLoginLogController extends AbstractXController<UserLoginLog> {
    @Resource
    IUserLoginLogService iUserLoginLogService;

    public UserLoginLogController(HttpServletRequest httpServletRequest) {
        super(httpServletRequest);
    }

    /**
     * 获取用户的登录日志
     *
     * @return
     * @throws XException
     */
    @GetMapping
    public R getLoginLog(@RequestParam Integer current, @RequestParam Integer size) throws XException {
        return data("MongoDB 还没实现");
    }

}

