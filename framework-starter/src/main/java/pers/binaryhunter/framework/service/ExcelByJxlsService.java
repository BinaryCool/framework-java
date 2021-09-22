package pers.binaryhunter.framework.service;

import java.io.OutputStream;
import java.util.Map;

public interface ExcelByJxlsService {
    /**
     * 下载
     * @return 下载的远程excel url
     */
    String download(GenericService service, String templateXls, Map<String, Object> params, OutputStream out);
}
