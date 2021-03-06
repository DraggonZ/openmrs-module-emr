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

package org.openmrs.module.emr.fragment.controller.paperrecord;

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.module.emr.paperrecord.PaperRecordService;
import org.openmrs.ui.framework.SimpleObject;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 */
public class RequestPaperRecordFragmentController {

    public SimpleObject requestPaperRecord(UiUtils ui,
                                           @RequestParam("patientId") Patient patient,
                                           @RequestParam("locationId") Location location,
                                           @SpringBean("paperRecordService") PaperRecordService service) {

        service.requestPaperRecord(patient, location, location);

        return SimpleObject.create("message", ui.message("emr.patientDashBoard.requestPaperRecord.successMessage"));
    }
}
