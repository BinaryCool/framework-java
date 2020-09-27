package pers.binaryhunter.db.es.bean.po;

import lombok.Getter;
import lombok.Setter;
import pers.binaryhunter.framework.bean.po.PO;

/**
 * Es对象基类
 */
@Getter
@Setter
public class EsPO extends PO {
    private String esKey;
}
