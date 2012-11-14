/*
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

package org.openmrs.module.emr.page.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.emr.EmrConstants;
import org.openmrs.module.emr.printer.Printer;
import org.openmrs.module.emr.printer.PrinterService;
import org.openmrs.ui.framework.annotation.BindParams;
import org.openmrs.ui.framework.annotation.MethodParam;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

public class PrinterPageController {

    protected final Log log = LogFactory.getLog(getClass());

    public Printer getPrinter(@RequestParam(value = "printerId", required = false) Printer printer) {
        if (printer == null) {
            printer = new Printer();

            // TODO: temporary, until we add this option
            printer.setType(Printer.Type.ID_CARD);
        }

        return printer;
    }

    public void get(PageModel model, @MethodParam("getPrinter") Printer printer) {
        model.addAttribute("printer", printer);
    }

    public String post(PageModel model, @MethodParam("getPrinter") @BindParams Printer printer, BindingResult errors,
                       @SpringBean("printerService")PrinterService printerService,
                       HttpServletRequest request) {


        // TODO: add validation

        if (!errors.hasErrors()) {

            try {
                printerService.savePrinter(printer);
                request.getSession().setAttribute(EmrConstants.SESSION_ATTRIBUTE_INFO_MESSAGE, "emr.printer.saved");

                return "redirect:/emr/managePrinters.page";
            }
            catch (Exception e) {
                log.warn("Some error occured while saving account details:", e);
                request.getSession().setAttribute(EmrConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
                        "emr.printer.error.save.fail");
            }
        } else {
            request.getSession().setAttribute(EmrConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
                    "emr.error.foundValidationErrors");
        }

        //redisplay the form
        model.addAttribute("printer", printer);
        return null;
    }

}