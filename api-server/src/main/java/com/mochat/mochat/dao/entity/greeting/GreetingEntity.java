package com.mochat.mochat.dao.entity.greeting;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * @description:好犹豫欢迎语
 * @author: Huayu
 * @time: 2021/2/1 15:26
 */
@TableName("mc_greeting")
@Data
public class GreetingEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer corpId; //企业ID
    private String type;//欢迎语类型
    private String words; //欢迎语文本
    private Integer mediumId;//欢迎语素材
    private Integer rangeType;//适用成员类型【1-全部成员(默认)】
    private String employees; //适用成员
    private Date createdAt;
    private Date updatedAt;
    private Date deletedAt;

}

