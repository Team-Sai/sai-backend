package org.teamsai.saibackend.domain.user.exception;

public class UserNotFoundException extends CustomException{
    public UserNotFoundException(ErrorCode errorCode){
        super(errorCode);
    }
}
