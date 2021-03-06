package org.openmrs.module.emr.radiology;

import org.openmrs.Encounter;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.emr.EmrContext;

public interface RadiologyService extends OpenmrsService {

    Encounter placeRadiologyRequisition(EmrContext emrContext, RadiologyRequisition requisition);
}
