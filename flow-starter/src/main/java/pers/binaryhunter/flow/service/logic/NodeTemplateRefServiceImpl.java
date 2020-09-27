
package pers.binaryhunter.flow.service.logic;

import pers.binaryhunter.framework.service.logic.GenericServiceImpl;
import org.springframework.stereotype.Service;
import pers.binaryhunter.flow.bean.po.NodeTemplateRef;
import pers.binaryhunter.flow.service.NodeTemplateRefService;

/**
 * 表：NodeTemplateRef Service Impl
 * @author BinaryHunter
 */
@Service("nodeTemplateRefService")
public class NodeTemplateRefServiceImpl extends GenericServiceImpl<NodeTemplateRef, Long> implements NodeTemplateRefService {
	
}
