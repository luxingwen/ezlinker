package com.ezlinker.app.modules.dataentry.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ezlinker.app.modules.device.model.Device;
import com.ezlinker.app.modules.device.service.IDeviceService;
import com.ezlinker.app.modules.module.model.Module;
import com.ezlinker.app.modules.module.model.ModuleLog;
import com.ezlinker.app.modules.module.service.IModuleService;
import com.ezlinker.app.modules.module.service.ModuleLogService;
import com.ezlinker.app.common.exception.XException;
import com.ezlinker.app.common.exchange.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Date;

/**
 * @program: ezlinker
 * @description: EMQ web hook插件的回调
 * @author: wangwenhai
 * @create: 2019-12-25 16:23
 **/
@RestController
@RequestMapping("/data")
@Slf4j
public class WebHookController {
    @Resource
    IModuleService iModuleService;

    @Resource
    ModuleLogService moduleLogService;

    @PostMapping("/webHook")
    public R publish(@RequestBody @Valid JSONObject message) throws XException {
        // 此处过滤EZLinker,直接放行
//        String clientId = message.getString("clientid");
//        if (clientId == null) {
//            clientId = message.getString("from_client_id");
//        }
//        if (clientId.startsWith("ezlinker")) {
//            return new R();
//        }

        /**
         * 筛选消息类型
         */
        String action = message.getString("action");
        switch (action) {
            case "client_connected":
                handConnect(message.getString("clientid"));
                break;
            case "client_disconnected":
                handDisconnect(message.getString("clientid"));
                break;
            case "message_publish":
                handMessage(message);
                break;
            default:
                break;
        }
        return R.ok();
    }

    /**
     * 连接处理
     *
     * @param clientId
     * @throws XException
     */

    @Resource
    IDeviceService iDeviceService;
    //TODO 此处应该把模块和设备合为一个查询，避免多次查库。后期优化

    private void handConnect(String clientId) throws XException {
        log.info("客户端[{}]连接成功", clientId);
        Module module = iModuleService.getOne(new QueryWrapper<Module>().eq("client_id", clientId));
        if (module == null) {
            throw new XException("Module not exist", "模块不存在");
        }
        Device device = iDeviceService.getById(module.getDeviceId());

        module.setLastActiveTime(new Date());
        module.setStatus(1);
        iModuleService.updateById(module);
        // 保存日志
        ModuleLog moduleLog = new ModuleLog();
        moduleLog.setSn(device.getSn());
        moduleLog.setDeviceName(device.getName());
        moduleLog.setModuleName(module.getName());
        moduleLog.setModuleId(module.getId());
        moduleLog.setType(ModuleLog.CONNECT);
        moduleLog.setCreateTime(new Date());
        moduleLogService.save(moduleLog);

    }

    /**
     * 客户端离线
     *
     * @param clientId
     * @throws XException
     */
    private void handDisconnect(String clientId) throws XException {
        log.info("客户端[{}]离线", clientId);
        Module module = iModuleService.getOne(new QueryWrapper<Module>().eq("client_id", clientId));
        if (module == null) {
            throw new XException("Module not exist", "模块不存在");
        }
        Device device = iDeviceService.getById(module.getDeviceId());

        module.setStatus(0);
        iModuleService.updateById(module);
        // 保存日志
        ModuleLog moduleLog = new ModuleLog();
        moduleLog.setSn(device.getSn());
        moduleLog.setDeviceName(device.getName());
        moduleLog.setModuleName(module.getName());
        moduleLog.setModuleId(module.getId());
        moduleLog.setType(ModuleLog.DISCONNECT);
        moduleLog.setCreateTime(new Date());
        moduleLogService.save(moduleLog);

    }

    /**
     * 消息到达
     *
     * @param message
     */
    private void handMessage(JSONObject message) {

    }
}
