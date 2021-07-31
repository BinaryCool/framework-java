package pers.binaryhunter.framework.exception;

import com.alibaba.fastjson.JSON;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pers.binaryhunter.framework.controller.GenericController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;

@ControllerAdvice
public class GlobalExceptionResolver extends GenericController {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionResolver.class);
    /**
     * 用来处理bean validation异常
     *
     * @param ex
     * @return
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

        errorLogException(ex);
        return toResponse(new BusinessException(errorMessage));
    }

    @ResponseBody
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Object resolveMethodArgumentNotValidException(Exception ex) {
        List<ObjectError> objectErrors = null;

        String errorMessage = ex.getMessage();
        if (!CollectionUtils.isEmpty(objectErrors)) {
            StringBuilder msgBuilder = new StringBuilder();
            for (ObjectError objectError : objectErrors) {
                String msg = objectError.getDefaultMessage();
                msgBuilder.append(msg).append(",");
            }

            errorMessage = msgBuilder.toString();
            if (errorMessage.length() > 1) {
                errorMessage = errorMessage.substring(0, errorMessage.length() - 1);
            }
        }
        errorLogException(ex);
        return toResponse(new BusinessException(errorMessage));
    }

    @ResponseBody
    @ExceptionHandler(value = Exception.class)
    public Object defaultErrorHandler(Exception ex) {
        if (!(ex instanceof SessionOutException)) {
            if (ex instanceof BusinessException || ex instanceof ClientAbortException) {
                warnLogException(ex);
            } else {
                errorLogException(ex);
            }
        }
        return toResponse(ex);
    }

    private void warnLogParams() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        log.warn("==> " + request.getRequestURI());
        log.warn(JSON.toJSONString(request.getParameterMap()));
    }

    private void errorLogParams() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        log.error("==> " + request.getRequestURI());
        log.error(JSON.toJSONString(request.getParameterMap()));
    }

    private void warnLogException(Exception ex) {
        warnLogParams();
        log.warn(ex.getMessage());
    }

    private void warnLogException(Exception ex, String msg) {
        warnLogParams();
        log.warn(msg);
    }

    private void errorLogException(Exception ex) {
        errorLogParams();
        log.error("", ex);
    }
}
