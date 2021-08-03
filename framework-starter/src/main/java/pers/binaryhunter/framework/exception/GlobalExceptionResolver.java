package pers.binaryhunter.framework.exception;

import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import pers.binaryhunter.framework.controller.GenericController;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.Set;

@ControllerAdvice
public class GlobalExceptionResolver extends GenericController {
    /**
     * 用来处理bean validation异常
     */
    @ResponseBody
    @ExceptionHandler(ConstraintViolationException.class)
    public Object resolveConstraintViolationException(ConstraintViolationException ex) {
        Set<ConstraintViolation<?>> constraintViolations = ex.getConstraintViolations();
        String errorMessage = ex.getMessage();
        if (!CollectionUtils.isEmpty(constraintViolations)) {
            StringBuilder msgBuilder = new StringBuilder();
            for (ConstraintViolation constraintViolation : constraintViolations) {
                String msg = constraintViolation.getMessage();
                msgBuilder.append(msg).append(",");
            }

            errorMessage = msgBuilder.toString();
            if (errorMessage.length() > 1) {
                errorMessage = errorMessage.substring(0, errorMessage.length() - 1);
            }
        }

        return toResponse(new BusinessException(errorMessage));
    }

    @ResponseBody
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Object resolveMethodArgumentNotValidException(Exception ex) {
        return toResponse(new BusinessException(ex.getMessage()));
    }

    @ResponseBody
    @ExceptionHandler(value = Exception.class)
    public Object defaultErrorHandler(Exception ex) {
        return toResponse(ex);
    }
}
