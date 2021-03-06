package com.ezlinker.app.modules.device.controller;


import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezlinker.app.common.web.CurdController;
import com.ezlinker.app.constants.DeviceState;
import com.ezlinker.app.modules.device.model.Device;
import com.ezlinker.app.modules.device.pojo.DeviceStatus;
import com.ezlinker.app.modules.device.pojo.FieldParam;
import com.ezlinker.app.modules.device.service.IDeviceService;
import com.ezlinker.app.modules.feature.model.Feature;
import com.ezlinker.app.modules.feature.pojo.Cmd;
import com.ezlinker.app.modules.feature.service.IFeatureService;
import com.ezlinker.app.modules.module.model.Module;
import com.ezlinker.app.modules.module.pojo.DataArea;
import com.ezlinker.app.modules.module.service.IModuleService;
import com.ezlinker.app.modules.moduletemplate.model.ModuleTemplate;
import com.ezlinker.app.modules.moduletemplate.service.IModuleTemplateService;
import com.ezlinker.app.modules.mqtttopic.model.MqttTopic;
import com.ezlinker.app.modules.mqtttopic.service.IMqttTopicService;
import com.ezlinker.app.modules.product.model.Product;
import com.ezlinker.app.modules.product.service.IProductService;
import com.ezlinker.app.modules.relation.service.IRelationProductModuleService;
import com.ezlinker.app.modules.tag.model.Tag;
import com.ezlinker.app.modules.tag.service.ITagService;
import com.ezlinker.app.utils.IDKeyUtil;
import com.ezlinker.app.utils.ModuleTokenUtil;
import com.ezlinker.app.common.exception.BizException;
import com.ezlinker.app.common.exception.XException;
import com.ezlinker.app.common.exchange.R;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.*;

/**
 * <p>
 * 实际设备，是产品的一个实例。 前端控制器
 * </p>
 *
 * @author wangwenhai
 * @since 2019-11-19
 */
@RestController
@RequestMapping("/devices")
public class DeviceController extends CurdController<Device> {
    // 发布权限
    private static final int TOPIC_PUB = 1;
    // 订阅权限
    private static final int TOPIC_SUB = 2;

    @Resource
    IModuleService iModuleService;
    @Resource
    IDeviceService iDeviceService;

    @Resource
    IProductService iProductService;
    @Resource
    ITagService iTagService;
    @Resource
    IMqttTopicService iMqttTopicService;

    @Resource
    IDKeyUtil idKeyUtil;

    @Resource
    IRelationProductModuleService iRelationProductModuleService;

    @Resource
    IModuleTemplateService iModuleTemplateService;

    public DeviceController(HttpServletRequest httpServletRequest) {
        super(httpServletRequest);
    }

    /**
     * 新建一个设备
     *
     * @param form
     * @return
     * @throws XException
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    protected R add(@RequestBody @Valid Device form) throws XException {

        Product product = iProductService.getById(form.getProductId());

        if (product == null) {
            throw new BizException("Product not exists", "该产品不存在!");
        }

        Device device = new Device();
        device.setName(form.getName())
                .setName(form.getName())
                .setDescription(form.getDescription())
                .setLocation(form.getLocation())
                .setModel(form.getModel())
                .setIndustry(form.getIndustry())
                //继承
                .setParameters(product.getParameters())
                .setProductId(product.getId())
                .setProjectId(product.getProjectId())
                .setLogo(product.getLogo())
                .setSn("SN" + idKeyUtil.nextId());
        // 保存设备
        iDeviceService.save(device);


        List<ModuleTemplate> moduleTemplates = iModuleTemplateService.list(new QueryWrapper<ModuleTemplate>().eq("product_id", product.getId()));


        for (ModuleTemplate moduleTemplate : moduleTemplates) {
            Module newModule = new Module();
            String clientId = IDKeyUtil.generateId().toString();
            String username = SecureUtil.md5(clientId);
            String password = SecureUtil.md5(username);
            newModule.setName(moduleTemplate.getName()).setDataAreas(moduleTemplate.getDataAreas());
            newModule.setClientId(clientId).setUsername(username).setPassword(password).setDeviceId(device.getId()).setProtocol(moduleTemplate.getProtocol());
            // Token
            List<DataArea> dataAreas = moduleTemplate.getDataAreas();


            ObjectMapper objectMapper = new ObjectMapper();
            List<DataArea> dataAreasList = objectMapper.convertValue(moduleTemplate.getDataAreas(), new TypeReference<List<DataArea>>() {
            });

            //
            List<String> fields = new ArrayList<>();
            for (DataArea area : dataAreasList) {
                fields.add(area.getField());
            }
            // 生成给Token，格式：clientId::[field1,field2,field3······]
            // token里面包含了模块的字段名,这样在数据入口处可以进行过滤。
            String token = ModuleTokenUtil.token(clientId + "::" + fields.toString());
            newModule.setToken(token);
            iModuleService.save(newModule);
            // 给新的Module创建Topic
            // 数据上行

            MqttTopic s2cTopic = new MqttTopic();
            s2cTopic.setAccess(TOPIC_SUB)
                    .setType(MqttTopic.S2C)
                    .setClientId(clientId)
                    .setModuleId(newModule.getId())
                    .setTopic("mqtt/out/" + SecureUtil.md5(device.getId().toString()) + "/" + clientId + "/s2c")
                    .setUsername(username);
            // 数据下行
            MqttTopic c2sTopic = new MqttTopic();
            c2sTopic.setAccess(TOPIC_PUB)
                    .setType(MqttTopic.C2S)
                    .setModuleId(newModule.getId())
                    .setClientId(clientId)
                    .setTopic("mqtt/in/" + SecureUtil.md5(device.getId().toString()) + "/" + clientId + "/c2s")
                    .setUsername(username);
            // 状态上报
            MqttTopic statusTopic = new MqttTopic();
            statusTopic.setAccess(TOPIC_PUB)
                    .setType(MqttTopic.STATUS)
                    .setUsername(username)
                    .setClientId(clientId)
                    .setModuleId(newModule.getId())
                    .setTopic("mqtt/in/" + SecureUtil.md5(device.getId().toString()) + "/" + clientId + "/status");
            //生成
            iMqttTopicService.save(s2cTopic);
            iMqttTopicService.save(c2sTopic);
            iMqttTopicService.save(statusTopic);

        }


        return data(device);
    }

    /**
     * 更新
     *
     * @param id
     * @param newDevice
     * @return
     * @throws XException
     */
    @PutMapping("/{id}")
    @Override
    protected R update(@PathVariable Long id, @RequestBody Device newDevice) throws XException {
        Device device = iDeviceService.getById(id);

        if (device == null) {
            throw new BizException("Device not exists", "该设备不存在!");
        }
        device.setName(newDevice.getName())
                .setName(newDevice.getName())
                .setDescription(newDevice.getDescription())
                .setLocation(newDevice.getLocation())
                .setModel(newDevice.getModel());

        iDeviceService.updateById(device);

        return data(device);
    }

    /**
     * 设备详情
     *
     * @param id
     * @return
     * @throws XException
     */
    @Override
    protected R get(@PathVariable Long id) throws XException {
        Device device = iDeviceService.getById(id);
        if (device == null) {
            throw new BizException("Device not exist", "设备不存在");
        }
        addTags(device);
        addModules(device);
        addFeatures(device);
        return data(device);
    }

    private void addTags(Device device) {
        List<Tag> tagList = iTagService.list(new QueryWrapper<Tag>().eq("link_id", device.getProductId()));
        Set<String> tags = new HashSet<>();
        for (Tag tag : tagList) {
            tags.add(tag.getName());
        }
        device.setTags(tags);
    }

    /**
     * 获取属性
     *
     * @param id
     * @return
     * @throws XException
     */
    @GetMapping("/status/{id}")
    public R getStatus(@PathVariable Long id) throws XException {
        Device device = iDeviceService.getById(id);
        if (device == null) {
            throw new BizException("Device not exist", "设备不存在");
        }
        return data(device.getStatuses());
    }

    /**
     * 设置状态
     *
     * @param id
     * @param statuses
     * @return
     * @throws XException
     */
    @PutMapping("/status/{id}")
    public R setStatus(@PathVariable Long id,
                       @Valid @RequestBody List<@Valid DeviceStatus> statuses,
                       ObjectMapper objectMapper) throws XException {
        Device device = iDeviceService.getById(id);
        if (device == null) {
            throw new BizException("Device not exist", "设备不存在");
        }
        /**
         * 提取KeySet集合
         */
        List<FieldParam> deviceParamList = objectMapper.convertValue(device.getParameters(), new TypeReference<List<FieldParam>>() {
        });

        Set<String> fields = new HashSet<>();
        for (FieldParam deviceParam : deviceParamList) {
            fields.add(deviceParam.getField());
        }
        /**
         * 比对
         */
        List<DeviceStatus> deviceStatusList = new ArrayList<>();

        for (DeviceStatus deviceStatus : statuses) {
            if (fields.contains(deviceStatus.getField())) {
                deviceStatusList.add(deviceStatus);
            }
        }
        device.setStatuses(deviceStatusList);
        boolean ok = iDeviceService.updateById(device);

        return ok ? data(deviceStatusList) : fail();

    }

    /**
     * 删除
     *
     * @param ids
     * @return
     * @throws XException
     */
    @Override
    protected R delete(@PathVariable Integer[] ids) {
        boolean ok = iDeviceService.removeByIds(Arrays.asList(ids));
        return ok ? success() : fail();
    }

    /**
     * 条件检索
     *
     * @param current
     * @param size
     * @param name
     * @param type
     * @return
     */
    @GetMapping
    public R queryForPage(
            @RequestParam Long productId,
            @RequestParam Integer current,
            @RequestParam Integer size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String sn,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String model) throws BizException {

        QueryWrapper<Device> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(projectId != null, "project_id", projectId);
        queryWrapper.eq(productId != null, "product_id", productId);
        queryWrapper.eq(sn != null, "sn", sn);
        queryWrapper.eq(model != null, "model", model);
        queryWrapper.eq(type != null, "type", type);
        queryWrapper.like(name != null, "name", name);
        queryWrapper.like(industry != null, "industry", industry);
        queryWrapper.orderByDesc("create_time");

        IPage<Device> devicePage = iDeviceService.page(new Page<>(current, size), queryWrapper);
        for (Device device : devicePage.getRecords()) {
            addTags(device);
            addModules(device);
            addFeatures(device);
        }

        return data(devicePage);
    }

    private void addModules(Device device) {
        List<Module> modules = iModuleService.list(new QueryWrapper<Module>().eq("device_id", device.getId()));
        device.setModules(modules);
    }

    @Resource
    IFeatureService iFeatureService;

    private void addFeatures(Device device) {
        List<Feature> features = iFeatureService.list(new QueryWrapper<Feature>().eq("product_id", device.getProductId()));
        device.setFeatures(features);
    }

    /**
     * 初始化设备
     *
     * @return
     */
    @PutMapping("/init/{id}")
    public R initialDevice(@PathVariable Long id) throws BizException {
        Device device = iDeviceService.getById(id);
        if (device == null) {
            throw new BizException("Device not exist", "设备不存在");
        }
        device.setLastActive(null);
        device.setState(DeviceState.UN_ACTIVE);
        return success();
    }


    /**
     * 推送指令
     * @param ids
     * @param cmdValues
     * @return
     */
    @PostMapping("/{ids}/action")
    public R pushCmd(@PathVariable List<Long> ids, @RequestBody List<Cmd> cmdValues) {


        return data(cmdValues);
    }


}

