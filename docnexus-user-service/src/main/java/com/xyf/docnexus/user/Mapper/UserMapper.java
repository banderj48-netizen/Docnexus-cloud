package com.xyf.docnexus.user.Mapper;

import com.xyf.docnexus.common.DTO.UserDTO;
import com.xyf.docnexus.common.DTO.UserProfileUpdateRequest;
import com.xyf.docnexus.common.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    UserDTO selectByUsername(String username);

    Integer countByUsername(@Param("username") String username);

    Integer insertUser(User User);

    Integer updatePasswordById(@Param("id") Long id, @Param("password") String password);

    Long selectTokenVersionById(Long id);

    Integer increaseTokenVersionById(Long id);

    UserDTO selectById(@Param("id") Long id);

    Integer updateProfileById(UserProfileUpdateRequest request);
}
