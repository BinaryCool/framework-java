package pers.binaryhunter.framework.exception;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pers.binaryhunter.framework.annotation.FieldName;
import pers.binaryhunter.framework.controller.GenericController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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
                Path p = constraintViolation.getPropertyPath();
                if(null != p) {
                    String[] path = constraintViolation.getPropertyPath().toString().split("\\.");
                    if(ArrayUtils.isEmpty(path)) {
                        msgBuilder.append(constraintViolation.getPropertyPath().toString());
                        continue;
                    }

                    boolean nameAppend = false;
                    if(1 < path.length) {
                        String methodName = path[0];
                        Class clazz = constraintViolation.getRootBeanClass();
                        Method method = null;
                        for (Method m : clazz.getMethods()) {
                            if (m.getName().equals(methodName)) {
                                method = m;
                                break;
                            }
                        }
                        String paramName = path[1];
                        if (null != method) {
                            for(Parameter param : method.getParameters()) {
                                // 如果只有一个参数, 参数名称会变为args0, 故只有一个参数也要进入
                                if(param.getName().equals(paramName) || 1 == method.getParameters().length) {
                                    if (param.isAnnotationPresent(FieldName.class)) {
                                        String value = param.getAnnotation(FieldName.class).value();
                                        if(StringUtils.isNotBlank(value)) {
                                            msgBuilder.append(value);
                                            nameAppend = true;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }

                    if(!nameAppend) {
                        msgBuilder.append(path[path.length - 1]);
                    }
                }
                msgBuilder.append(constraintViolation.getMessage()).append(",");
            }

            errorMessage = msgBuilder.toString();
            if (errorMessage.length() > 1) {
                errorMessage = errorMessage.substring(0, errorMessage.length() - 1);
            }
            errorMessage = processNull(errorMessage);
        }

        warnLogException(ex, errorMessage);
        return toResponse(new BusinessException(errorMessage));
    }

    @ResponseBody
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Object resolveMethodArgumentNotValidException(Exception ex) {
        List<ObjectError> objectErrors = null;
        Object target = null;
        if(ex instanceof BindException) {
            BindingResult bindingResult = ((BindException) ex).getBindingResult();
            objectErrors = bindingResult.getAllErrors();
            target = bindingResult.getTarget();
        } else if (ex instanceof MethodArgumentNotValidException) {
            BindingResult bindingResult = ((MethodArgumentNotValidException) ex).getBindingResult();
            objectErrors = bindingResult.getAllErrors();
            target = bindingResult.getTarget();
        }

        String errorMessage = ex.getMessage();
        if (!CollectionUtils.isEmpty(objectErrors)) {
            StringBuilder msgBuilder = new StringBuilder();
            for (ObjectError objectError : objectErrors) {
                if(objectError instanceof FieldError) {
                    FieldError fieldError = ((FieldError) objectError);
                    String field = fieldError.getField();
                    boolean nameAppend = false;
                    if(null != target) {
                        try {
                            Field errorFiled = target.getClass().getDeclaredField(field);
                            if (errorFiled.isAnnotationPresent(FieldName.class)) {
                               String value = errorFiled.getAnnotation(FieldName.class).value();
                               if(StringUtils.isNotBlank(value)) {
                                   msgBuilder.append(value);
                                   nameAppend = true;
                               }
                            }
                        } catch (Exception e) {
                            log.warn(e.getMessage());
                        }
                    }

                    if(!nameAppend) {
                        msgBuilder.append(field);
                    }
                }
                msgBuilder.append(objectError.getDefaultMessage()).append(",");
            }

            errorMessage = msgBuilder.toString();
            if (errorMessage.length() > 1) {
                errorMessage = errorMessage.substring(0, errorMessage.length() - 1);
            }
            errorMessage = processNull(errorMessage);
        }
        warnLogException(ex, errorMessage);
        return toResponse(new BusinessException(errorMessage));
    }

    @ResponseBody
    @ExceptionHandler(value = Exception.class)
    public Object defaultErrorHandler(Exception ex) {
        if (!(ex instanceof SessionOutException)) {
            if (ex instanceof BusinessException) {
                warnLogException(ex);
            } else {
                errorLogException(ex);
            }
        }
        return toResponse(ex);
    }

    private String processNull(String msg) {
        if(StringUtils.isBlank(msg)) {
            return msg;
        }
        return msg.replaceAll("null", "空");
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
