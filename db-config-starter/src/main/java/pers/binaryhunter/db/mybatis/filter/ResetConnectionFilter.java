package pers.binaryhunter.db.mybatis.filter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import pers.binaryhunter.db.mybatis.datasource.ConnectionHolder;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by BinaryHunter on 2018/9/26.
 */
public class ResetConnectionFilter extends OncePerRequestFilter {
    private static final Log log = LogFactory.getLog(ResetConnectionFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        if(log.isDebugEnabled()) log.debug("Reset ConnectionHolder.FORCE_WRITE to false");
        ConnectionHolder.FORCE_WRITE.set(Boolean.FALSE);
    }
}
