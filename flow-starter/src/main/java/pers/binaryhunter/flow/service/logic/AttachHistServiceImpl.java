
package pers.binaryhunter.flow.service.logic;

import pers.binaryhunter.framework.service.logic.GenericServiceImpl;
import org.springframework.stereotype.Service;
import pers.binaryhunter.flow.bean.po.AttachHist;
import pers.binaryhunter.flow.service.AttachHistService;

/**
 * 表：AttachHist Service Impl
 * @author BinaryHunter
 */
@Service("attachHistService")
public class AttachHistServiceImpl extends GenericServiceImpl<AttachHist, Long> implements AttachHistService {
	
}
