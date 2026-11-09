-- 仅用于隔离测试库；按补货功能需要保留既有业务表的最小字段集合。
CREATE TABLE dodo_goods (
    goods_id BIGINT PRIMARY KEY COMMENT '商品标识',
    store_id BIGINT COMMENT '所属租户门店标识',
    goods_name VARCHAR(255) COMMENT '商品名称',
    goods_img VARCHAR(255) COMMENT '商品图片',
    goods_number DECIMAL(12,3) COMMENT '商品库存量',
    is_on_sale INT COMMENT '是否上架',
    status INT COMMENT '记录有效状态',
    is_delete INT COMMENT '是否删除',
    is_virtual INT COMMENT '是否虚拟商品',
    min_sales_num DECIMAL(12,3) COMMENT '最小销售数量',
    sales_step_width DECIMAL(12,3) COMMENT '销售数量步长',
    share_stock_id BIGINT COMMENT '共享库存标识',
    delay_id BIGINT COMMENT '延迟销售设置标识'
) ENGINE=InnoDB COMMENT='测试用既有商品表';

CREATE TABLE dodo_goods_share_stock (
    id BIGINT PRIMARY KEY COMMENT '共享库存标识',
    goods_number DECIMAL(12,3) COMMENT '共享可售库存量',
    is_on_sale INT COMMENT '是否上架',
    status INT COMMENT '记录有效状态'
) ENGINE=InnoDB COMMENT='测试用既有共享库存表';

CREATE TABLE dodo_goods_delay (
    id BIGINT PRIMARY KEY COMMENT '延迟销售设置标识',
    arrival_time DATETIME COMMENT '预计允许销售时间',
    status INT COMMENT '记录有效状态',
    delay_status INT COMMENT '延迟销售状态',
    store_id BIGINT COMMENT '所属租户门店标识'
) ENGINE=InnoDB COMMENT='测试用既有延迟销售设置';

CREATE TABLE dodo_shop_goods (
    shop_id BIGINT COMMENT '选购店铺标识',
    goods_id BIGINT COMMENT '商品标识',
    status INT COMMENT '关系有效状态'
) ENGINE=InnoDB COMMENT='测试用既有店铺商品关系';

CREATE TABLE dodo_dict (
    id BIGINT PRIMARY KEY COMMENT '字典记录标识',
    store_id BIGINT COMMENT '所属租户门店标识',
    dict_type VARCHAR(50) COMMENT '字典类型',
    dict_key VARCHAR(50) COMMENT '字典键',
    dict_value VARCHAR(100) COMMENT '字典值',
    status INT COMMENT '记录有效状态'
) ENGINE=InnoDB COMMENT='测试用既有门店销售设置';

CREATE TABLE dodo_order_info (
    order_id BIGINT PRIMARY KEY COMMENT '订单标识',
    user_id BIGINT COMMENT '下单用户标识',
    store_id BIGINT COMMENT '所属租户门店标识',
    shop_id BIGINT COMMENT '选购店铺标识',
    status INT COMMENT '记录有效状态',
    pay_status INT COMMENT '支付状态',
    order_status INT COMMENT '订单状态',
    create_time DATETIME(3) COMMENT '订单创建时间'
) ENGINE=InnoDB COMMENT='测试用既有订单主表';

CREATE TABLE dodo_order_goods (
    order_id BIGINT COMMENT '订单标识',
    goods_id BIGINT COMMENT '商品标识',
    status INT COMMENT '记录有效状态',
    goods_number INT COMMENT '购买数量'
) ENGINE=InnoDB COMMENT='测试用既有订单商品表';

CREATE TABLE dodo_users (
    user_id BIGINT PRIMARY KEY COMMENT '用户标识',
    client_id VARCHAR(100) COMMENT '推送设备标识',
    is_subscribe_msg INT COMMENT '是否同意消息订阅',
    is_open_push INT COMMENT '是否开启应用推送',
    status INT COMMENT '记录有效状态'
) ENGINE=InnoDB COMMENT='测试用既有用户通知设置';

CREATE TABLE dodo_admin_user (
    user_id INT PRIMARY KEY COMMENT '管理员标识',
    name VARCHAR(50) COMMENT '管理员名称'
) ENGINE=InnoDB COMMENT='测试用既有管理员表';

-- 内容字段使用迁移前的 200 字定义，由实际迁移脚本扩容；验证复用时不会丢失完整凭据。
CREATE TABLE dodo_operate_log (
    id INT NOT NULL AUTO_INCREMENT COMMENT '操作日志标识',
    store_id INT NULL COMMENT '所属租户门店标识',
    store_name VARCHAR(20) NULL COMMENT '门店名称',
    operate_user_id INT NULL COMMENT '操作管理员标识',
    operate_user_name VARCHAR(50) NULL COMMENT '操作管理员名称',
    operate_content VARCHAR(200) NULL COMMENT '迁移前的操作内容',
    operate_type VARCHAR(10) NULL COMMENT '操作类型',
    create_time TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    update_time TIMESTAMP NULL COMMENT '更新时间',
    status TINYINT DEFAULT 1 COMMENT '记录有效状态',
    PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='测试用既有业务主库操作日志';
