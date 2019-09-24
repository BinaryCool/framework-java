/*
 Navicat Premium Data Transfer

 Source Server         : yh-fs-dev
 Source Server Type    : MySQL
 Source Server Version : 50628
 Source Host           : 10.0.66.130:3306
 Source Schema         : yhcs

 Target Server Type    : MySQL
 Target Server Version : 50628
 File Encoding         : 65001

 Date: 24/09/2019 16:32:00
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for f_attach_curr
-- ----------------------------
DROP TABLE IF EXISTS `f_attach_curr`;
CREATE TABLE `f_attach_curr` (
  `id` int(11) NOT NULL COMMENT 'ID',
  `id_node` int(11) DEFAULT NULL COMMENT '节点ID',
  `attach_url` varchar(200) DEFAULT NULL COMMENT '附件URL',
  `attach_type` varchar(20) DEFAULT NULL COMMENT '附件类型',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL COMMENT '更新时间',
  `status` mediumint(1) DEFAULT '1' COMMENT '状态(0:删除, 1:正常)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='当前节点附件';

-- ----------------------------
-- Table structure for f_attach_hist
-- ----------------------------
DROP TABLE IF EXISTS `f_attach_hist`;
CREATE TABLE `f_attach_hist` (
  `id` int(11) NOT NULL COMMENT 'ID',
  `id_node` int(11) DEFAULT NULL COMMENT '节点ID',
  `attach_url` varchar(200) DEFAULT NULL COMMENT '附件URL',
  `attach_type` varchar(20) DEFAULT NULL COMMENT '附件类型',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL COMMENT '更新时间',
  `status` mediumint(1) DEFAULT '1' COMMENT '状态(0:删除, 1:正常)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史节点附件';

-- ----------------------------
-- Table structure for f_flow
-- ----------------------------
DROP TABLE IF EXISTS `f_flow`;
CREATE TABLE `f_flow` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `code` varchar(100) DEFAULT NULL COMMENT '编码',
  `name` varchar(200) DEFAULT NULL COMMENT '名称',
  `cascade_code_entry` varchar(50) DEFAULT NULL COMMENT '入口层级',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL COMMENT '更新时间',
  `status` mediumint(1) DEFAULT '1' COMMENT '状态(0:删除, 1:正常)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `u_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COMMENT='流程';

-- ----------------------------
-- Table structure for f_node_curr
-- ----------------------------
DROP TABLE IF EXISTS `f_node_curr`;
CREATE TABLE `f_node_curr` (
  `id` int(11) NOT NULL COMMENT 'ID',
  `flow_code` varchar(100) DEFAULT NULL COMMENT '流程编码',
  `cascade_code` varchar(50) DEFAULT NULL COMMENT '层级',
  `action` varchar(200) DEFAULT NULL COMMENT '动作',
  `roles` varchar(200) DEFAULT NULL COMMENT '可操作角色(以,分割)',
  `deal_note` varchar(200) DEFAULT NULL COMMENT '处理意见',
  `deal_role` varchar(200) DEFAULT NULL COMMENT '处理人角色',
  `deal_user_id` int(11) DEFAULT NULL COMMENT '处理人ID',
  `deal_user_name` varchar(200) DEFAULT NULL COMMENT '处理人姓名',
  `deal_user_tel` varchar(20) DEFAULT NULL COMMENT '处理人联系方式',
  `id_bill` int(11) DEFAULT NULL COMMENT '单据ID',
  `node_desc` varchar(200) DEFAULT NULL COMMENT '描述',
  `template_id` int(11) DEFAULT NULL COMMENT '模板ID',
  `process_status` mediumint(1) DEFAULT NULL COMMENT '处理状态: 0(未完结)|1(已完结)',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL COMMENT '更新时间',
  `status` mediumint(1) DEFAULT '1' COMMENT '状态(0:删除, 1:正常)',
  PRIMARY KEY (`id`),
  KEY `i_flow_code` (`flow_code`),
  KEY `i_template_id` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='当前节点';

-- ----------------------------
-- Table structure for f_node_hist
-- ----------------------------
DROP TABLE IF EXISTS `f_node_hist`;
CREATE TABLE `f_node_hist` (
  `id` int(11) NOT NULL COMMENT 'ID',
  `flow_code` varchar(100) DEFAULT NULL COMMENT '流程编码',
  `cascade_code` varchar(50) DEFAULT NULL COMMENT '层级',
  `action` varchar(200) DEFAULT NULL COMMENT '动作',
  `roles` varchar(200) DEFAULT NULL COMMENT '可操作角色(以,分割)',
  `deal_note` varchar(200) DEFAULT NULL COMMENT '处理意见',
  `deal_role` varchar(200) DEFAULT NULL COMMENT '处理人角色',
  `deal_user_id` int(11) DEFAULT NULL COMMENT '处理人ID',
  `deal_user_name` varchar(200) DEFAULT NULL COMMENT '处理人姓名',
  `deal_user_tel` varchar(20) DEFAULT NULL COMMENT '处理人联系方式',
  `node_desc` varchar(200) DEFAULT NULL COMMENT '描述',
  `template_id` int(11) DEFAULT NULL COMMENT '模板ID',
  `id_bill` int(11) DEFAULT NULL COMMENT '单据ID',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL COMMENT '更新时间',
  `status` mediumint(1) DEFAULT '1' COMMENT '状态(0:删除, 1:正常)',
  PRIMARY KEY (`id`),
  KEY `index_id_bill` (`id_bill`),
  KEY `i_flow_code` (`flow_code`),
  KEY `i_template_id` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史节点';

-- ----------------------------
-- Table structure for f_node_template
-- ----------------------------
DROP TABLE IF EXISTS `f_node_template`;
CREATE TABLE `f_node_template` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `action` varchar(200) DEFAULT NULL COMMENT '动作',
  `flow_code` varchar(50) DEFAULT NULL COMMENT '流程编码',
  `cascade_code` varchar(50) DEFAULT NULL COMMENT '层级',
  `roles` varchar(200) DEFAULT NULL COMMENT '可操作角色(以,分割)',
  `node_desc` varchar(200) DEFAULT NULL COMMENT '节点描述',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL COMMENT '更新时间',
  `status` mediumint(1) DEFAULT '1' COMMENT '状态(0:删除, 1:正常)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `u_flowcode_cascadecode` (`flow_code`,`cascade_code`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COMMENT='节点模板';

-- ----------------------------
-- Table structure for f_node_template_ref
-- ----------------------------
DROP TABLE IF EXISTS `f_node_template_ref`;
CREATE TABLE `f_node_template_ref` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `flow_code` varchar(50) DEFAULT NULL COMMENT '流程编码',
  `curr_code` varchar(50) DEFAULT NULL COMMENT '当前节点ID',
  `next_code` varchar(50) DEFAULT NULL COMMENT '下一个节点ID',
  `action_key` varchar(200) DEFAULT NULL COMMENT '动作Key',
  `calc` varchar(10) DEFAULT NULL COMMENT '运算',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT NULL COMMENT '更新时间',
  `status` mediumint(1) DEFAULT '1' COMMENT '状态(0:删除, 1:正常)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COMMENT='模板节点关系';

SET FOREIGN_KEY_CHECKS = 1;
