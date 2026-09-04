package top.daoha.infrastructure.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.daoha.infrastructure.dao.po.AiConversation;

import java.util.List;

/**
 * 会话表 DAO。
 */
@Mapper
public interface IAiConversationDao {

    List<AiConversation> queryList(@Param("mode") String mode);

    AiConversation queryById(@Param("id") String id);

    int insertOrUpdate(AiConversation conversation);

    int updateTitle(@Param("id") String id, @Param("title") String title);

    int updateTime(@Param("id") String id);

    int deleteById(@Param("id") String id);

    int countById(@Param("id") String id);
}
