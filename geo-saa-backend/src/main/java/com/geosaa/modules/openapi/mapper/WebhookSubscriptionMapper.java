package com.geosaa.modules.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geosaa.modules.openapi.entity.WebhookSubscription;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WebhookSubscriptionMapper extends BaseMapper<WebhookSubscription> {
}
