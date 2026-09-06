package com.tuling.tim.gateway.exception;

import com.tuling.tim.common.exception.TIMException;
import com.tuling.tim.common.res.BaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @since JDK 1.8
 */
@ControllerAdvice
public class ExceptionHandlingController {

    private static Logger logger = LoggerFactory.getLogger(ExceptionHandlingController.class);

    @ExceptionHandler(TIMException.class)
    @ResponseBody()
    public BaseResponse handleAllExceptions(TIMException ex) {
        logger.error("exception", ex);
        BaseResponse baseResponse = new BaseResponse();
        baseResponse.setCode(ex.getErrorCode());
        baseResponse.setMessage(ex.getMessage());
        return baseResponse;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody()
    public BaseResponse handleValidationException(MethodArgumentNotValidException ex) {
        logger.error("validation exception", ex);
        BaseResponse baseResponse = new BaseResponse();
        baseResponse.setCode(com.tuling.tim.common.enums.StatusEnum.VALIDATION_FAIL.getCode());
        baseResponse.setMessage(ex.getBindingResult().getFieldError() == null
                ? com.tuling.tim.common.enums.StatusEnum.VALIDATION_FAIL.getMessage()
                : ex.getBindingResult().getFieldError().getDefaultMessage());
        return baseResponse;
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody()
    public BaseResponse handleUnexpectedException(Exception ex) {
        logger.error("unexpected exception", ex);
        BaseResponse baseResponse = new BaseResponse();
        baseResponse.setCode(com.tuling.tim.common.enums.StatusEnum.FAIL.getCode());
        // Keep the response actionable without exposing credentials, payloads, or stack traces.
        baseResponse.setMessage("Request failed: " + ex.getClass().getSimpleName());
        return baseResponse;
    }

}
