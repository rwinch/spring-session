package sample;

import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author Rob Winch
 * @since 5.0
 */
public class SessionAttributeForm {
	private String attributeName;

	private String attributeValue;

	public String getAttributeName() {
		return attributeName;
	}

	public void setAttributeName(String attributeName) {
		this.attributeName = attributeName;
	}

	public String getAttributeValue() {
		return attributeValue;
	}

	public void setAttributeValue(String attributeValue) {
		this.attributeValue = attributeValue;
	}
}
