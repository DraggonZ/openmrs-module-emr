/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.emr.converter;

import org.openmrs.VisitType;
import org.openmrs.api.context.Context;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;


/**
 * Converts String to VisitType, interpreting it as a VisitType.id
 */
@Component
public class StringToVisitTypeConverter implements Converter<String, VisitType> {

	/**
     * @see org.springframework.core.convert.converter.Converter#convert(Object)
     */
    @Override
    public VisitType convert(String source) {
	    return Context.getVisitService().getVisitType(Integer.valueOf(source));
    }
	
}
