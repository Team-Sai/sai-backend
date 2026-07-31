package org.teamsai.saibackend.domain.user.exception;

public class MemoNotFoundException extends CustomException{
    public MemoNotFoundException(ErrorCode errorCode, String message){
        super(errorCode,message);
    }
}
