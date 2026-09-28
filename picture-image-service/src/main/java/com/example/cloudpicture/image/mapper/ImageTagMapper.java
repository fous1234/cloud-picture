package com.example.cloudpicture.image.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.cloudpicture.image.entity.ImageTag;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.dao.DuplicateKeyException;

public interface ImageTagMapper extends BaseMapper<ImageTag> {

    @Update("UPDATE t_image_tag SET use_count = use_count + 1 WHERE tag_name = #{tagName} AND is_delete = 0")
    int increaseUseCount(@Param("tagName") String tagName);

    @Update("UPDATE t_image_tag SET use_count = use_count - 1 WHERE tag_name = #{tagName} AND use_count > 0 AND is_delete = 0")
    int decreaseUseCount(@Param("tagName") String tagName);

    @Select("SELECT tag_name FROM t_image_tag WHERE is_delete = 0 ORDER BY use_count DESC, id ASC LIMIT #{limit}")
    List<String> listTagNames(@Param("limit") int limit);

    /** 标签入库并在字典中累加使用次数，字典里没有的标签先建字典项 */
    default void increaseTags(Collection<String> tags) {
        if (tags == null) {
            return;
        }
        for (String tag : tags) {
            if (increaseUseCount(tag) > 0) {
                continue;
            }
            ImageTag imageTag = new ImageTag();
            imageTag.setTagName(tag);
            imageTag.setUseCount(1);
            try {
                insert(imageTag);
            } catch (DuplicateKeyException ignored) {
                // 并发下另一个请求已建好字典项，次数由它累加
            }
        }
    }

    default void decreaseTags(Collection<String> tags) {
        if (tags == null) {
            return;
        }
        for (String tag : tags) {
            decreaseUseCount(tag);
        }
    }
}