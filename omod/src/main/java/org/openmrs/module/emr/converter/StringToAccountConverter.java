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

import org.apache.commons.lang.StringUtils;
import org.openmrs.module.emr.account.Account;
import org.openmrs.module.emr.account.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts String to Account, interpreting it as a user.id
 */
@Component
public class StringToAccountConverter implements Converter<String, Account> {
	
	@Autowired
	private AccountService accountService;
	
	/**
	 * @see org.springframework.core.convert.converter.Converter#convert(Object)
	 */
	@Override
	public Account convert(String personId) {
		if (StringUtils.isNotBlank(personId))
			return accountService.getAccount(Integer.valueOf(personId));
		return null;
	}
}