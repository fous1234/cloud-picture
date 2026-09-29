package com.example.cloudpicture.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_image_tag")
public class ImageTag {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String tagName;

    private Integer useCount;

    @TableLogic
    private Integer isDelete;
}