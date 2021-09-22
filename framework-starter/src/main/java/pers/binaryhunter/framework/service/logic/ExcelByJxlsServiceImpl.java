package pers.binaryhunter.framework.service.logic;

import ch.qos.logback.core.util.FileUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import pers.binaryhunter.framework.bean.dto.paging.Page;
import pers.binaryhunter.framework.exception.BusinessException;
import pers.binaryhunter.framework.service.ExcelByJxlsService;
import pers.binaryhunter.framework.service.GenericService;
import pers.binaryhunter.framework.utils.DateUtil;

import java.io.*;
import java.util.*;

public class ExcelByJxlsServiceImpl implements ExcelByJxlsService {
    @Override
    public String download(GenericService service, String templateXls, Map<String, Object> params, OutputStream out) {
        // TODO
        return null;
        /*
        InputStream is = null;
        try {
            Long totalCount = service.countByArgs(params);

            // File xlsx = new File(Constant.getResourcePath() + "/" + Constant.getTmpFolderName() + "/" + UUID.randomUUID().toString() + ".xlsx");
            if (StringUtils.isBlank(templateXls)) {
                throw new BusinessException("未找到模板文件");
            }

            is = new FileInputStream(Constant.getTomcatRootPath() + templateXls);

            JxlsHelper instance = JxlsHelper.getInstance();
            PoiTransformer transformer = (PoiTransformer) instance.createTransformer(is, out);
            List<Area> xlsAreaList = MyJxlsHelper.areaBuilder(instance, transformer);

            String startCellName = xlsAreaList.get(0).getStartCellRef().getCellName();

            String startCellNameWithoutTitle = MyJxlsHelper.getCellNameByOffset(startCellName, 1);
            CellRef startCellWithoutTitle = new CellRef(startCellNameWithoutTitle);
            List<Area> xlsWithoutTitle = new ArrayList<>();
            for (Area xlsArea : xlsAreaList) {
                Size originSize = xlsArea.getSize();
                Size size = new Size(originSize.getWidth(), originSize.getHeight());
                size.setHeight(size.getHeight() - 1);
                xlsWithoutTitle.add(new XlsArea(startCellWithoutTitle, size, xlsArea.getCommandDataList(), transformer));
            }

            Context context = new Context();

            Page page = new Page();
            page.setNumPerPage(Constant.getDownloadPageSize());
            page.setTotalCount(totalCount);

            boolean withTitle = true;
            int index = 1;
            for (int pageNum = 1; pageNum <= page.getPageCount(); pageNum++) {
                page.setPageNum(pageNum);

                int percent = (int)(pageNum * 100.0 / page.getPageCount());
                log.info("{}: {} / {} / {}", null == dto.getDownloadId() ? "" : dto.getDownloadId(), pageNum, page.getPageCount(), percent + "%");

                boolean normal = offlineDownloadService.progress(dto.getDownloadId(), percent);
                if (!normal) {
                    throw new BusinessException("客户自行取消下载任务");
                }

                List beans = service.pageSkipCount(dto.getParams(), page);
                if(CollectionUtils.isNotEmpty(beans)) {
                    service.putVar(context, beans);

                    MyJxlsHelper.applyContext(instance, withTitle ? xlsAreaList : xlsWithoutTitle, context, withTitle ? startCellName : MyJxlsHelper.getCellNameByOffset(startCellName, index));
                    withTitle = false;

                    index += beans.size();
                }
            }

            transformer.write();

            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DATE, -1);
            String toPath = "1/" + DateUtil.format(calendar.getTime(), DateUtil.PatternType.YYYYMMDD.getPattern()) + "/xlxs";
            String remoteXlsx = FileUtil.postFile(xlsx, toPath);
            offlineDownloadService.success(dto.getDownloadId(), remoteXlsx);
            log.info("Downloaded {}: {}", downloadTypeEnum, remoteXlsx);
            FileUtils.deleteQuietly(xlsx);
            return remoteXlsx;
        } catch (Exception ex) {
            if (ex instanceof BusinessException) {
                log.warn(ex.getMessage());
            } else {
                log.error("", ex);
            }
            String msg = ex.getMessage();
            if(null !=  msg && msg.length() > 200) {
                msg = msg.substring(0, 200);
            }
            offlineDownloadService.fail(dto.getDownloadId(), msg);

            throw new BusinessException(ex.getMessage());
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(out);
        }
        */
    }
}
