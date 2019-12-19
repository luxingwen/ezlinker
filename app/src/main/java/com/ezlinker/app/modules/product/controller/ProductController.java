package com.ezlinker.app.modules.product.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezlinker.app.common.XController;
import com.ezlinker.app.modules.product.model.Product;
import com.ezlinker.app.modules.product.service.IProductService;
import com.ezlinker.app.modules.relation.model.RelationProductTag;
import com.ezlinker.app.modules.relation.service.IRelationProductTagService;
import com.ezlinker.app.modules.tag.model.Tag;
import com.ezlinker.app.modules.tag.service.ITagService;
import com.ezlinker.common.exception.BizException;
import com.ezlinker.common.exception.XException;
import com.ezlinker.common.exchange.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 产品（设备的抽象模板） 前端控制器
 * </p>
 *
 * @author wangwenhai
 * @since 2019-11-13
 */
@RestController
@RequestMapping("/products")
public class ProductController extends XController {

    @Resource
    IProductService iProductService;

    public ProductController(HttpServletRequest httpServletRequest) {
        super(httpServletRequest);
    }

    @Resource
    ITagService iTagService;
    @Resource
    IRelationProductTagService iRelationProductTagService;

    /**
     * 创建产品
     *
     * @param product 产品:必传
     * @return
     */

    @PostMapping
    protected R add(@RequestBody @Valid Product product) {

        boolean ok = iProductService.save(product);
        if (ok) {
            if (product.getTags() != null) {
                for (String tag : product.getTags()) {
                    Tag t = new Tag();
                    t.setName(tag).setLinkId(product.getId());
                    iTagService.save(t);
                    RelationProductTag productTag = new RelationProductTag();
                    productTag.setProductId(product.getId()).setTagId(t.getId());
                    iRelationProductTagService.save(productTag);
                }
            }

        }
        return ok ? data(product) : fail();

    }

    /**
     * 删除产品
     *
     * @param ids 批量删除的ID数组:必传
     * @return
     */

    @DeleteMapping("/{ids}")
    public R delete(@PathVariable Integer[] ids) {
        boolean ok = iProductService.removeByIds(Arrays.asList(ids));
        return ok ? success() : fail();
    }

    /**
     * 更新产品信息
     *
     * @param newProduct 产品:必传
     * @return
     * @throws XException
     */
    @PutMapping("/{id}")
    public R update(@PathVariable Long id, @RequestBody @Valid Product newProduct) {

        Product product = iProductService.getById(id);
        newProduct.setId(product.getId());
        boolean ok = iProductService.updateById(newProduct);
        return ok ? data(product) : fail();
    }


    /**
     * 查看产品详情
     *
     * @param id 产品ID:必传
     * @return
     */


    @GetMapping("/{id}")
    public R get(@PathVariable Long id) throws XException {
        Product product = iProductService.getById(id);
        if (product == null) {
            throw new BizException("Product not exists!", "产品不存在");
        }
        List<Tag> tagList = iTagService.list(new QueryWrapper<Tag>().eq("link_id", id));
        Set<String> tags = new HashSet<>();

        for (Tag tag : tagList) {
            tags.add(tag.getName());
        }
        product.setTags(tags);
        return data(product);
    }

    /**
     * 查询
     *
     * @param projectId 所属项目ID
     * @param name      名称
     * @param type      类型
     * @param current   页码
     * @param size      页长
     * @return
     */
    @GetMapping
    public R queryForPage(
            @RequestParam Long projectId,
            @RequestParam Integer current,
            @RequestParam Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer type) {
        QueryWrapper<Product> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("project_id", projectId);
        queryWrapper.eq(type != null, "type", type);
        queryWrapper.like(name != null, "name", name);

        queryWrapper.orderByDesc("create_time");
        IPage<Product> projectPage = iProductService.page(new Page<>(current, size), queryWrapper);

        for (Product product : projectPage.getRecords()) {
            addTags(product);

        }

        return data(projectPage);
    }


    private void addTags(Product product) {
        List<Tag> tagList = iTagService.list(new QueryWrapper<Tag>().eq("link_id", product.getId()));
        Set<String> tags = new HashSet<>();
        for (Tag tag : tagList) {
            tags.add(tag.getName());
        }
        product.setTags(tags);
    }

}

