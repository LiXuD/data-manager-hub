package com.dataplatform.role.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.role.entity.RoleInfo;

public interface RoleService extends IService<RoleInfo> {
    Page<RoleInfo> listPage(int page, int pageSize, String keyword, String status);
}