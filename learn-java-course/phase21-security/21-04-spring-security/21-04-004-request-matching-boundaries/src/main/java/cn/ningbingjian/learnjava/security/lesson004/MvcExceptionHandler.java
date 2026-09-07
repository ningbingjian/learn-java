package cn.ningbingjian.learnjava.security.lesson004;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MvcExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(MvcExceptionHandler.class);

    @ExceptionHandler(UnsupportedLanguageException.class)
    ResponseEntity<ApiError> unsupportedLanguage() {
        log.info("MVC_ERROR_HANDLED");
        return ResponseEntity.badRequest().body(
                new ApiError("UNSUPPORTED_LANGUAGE", "lang只支持en或zh，请修改参数后重试。"));
    }
}
