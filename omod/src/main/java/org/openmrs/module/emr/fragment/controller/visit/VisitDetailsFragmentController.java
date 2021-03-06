package org.openmrs.module.emr.fragment.controller.visit;

import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.DateUtils;
import org.openmrs.*;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.EncounterService;
import org.openmrs.module.emr.EmrConstants;
import org.openmrs.module.emr.EmrContext;
import org.openmrs.ui.framework.SimpleObject;
import org.openmrs.ui.framework.UiFrameworkConstants;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.action.FailureResult;
import org.openmrs.ui.framework.fragment.action.FragmentActionResult;
import org.openmrs.ui.framework.fragment.action.SuccessResult;
import org.springframework.web.bind.annotation.RequestParam;

import java.text.ParseException;
import java.util.*;

public class VisitDetailsFragmentController {

    public SimpleObject getVisitDetails(
            @SpringBean("adminService") AdministrationService administrationService,
            @RequestParam("visitId") Visit visit,
            UiUtils uiUtils,
            EmrContext emrContext) throws ParseException {

        SimpleObject simpleObject = SimpleObject.fromObject(visit, uiUtils, "id", "location");
        User authenticatedUser = emrContext.getUserContext().getAuthenticatedUser();
        boolean canDelete = authenticatedUser.hasPrivilege(EmrConstants.PRIVILEGE_DELETE_ENCOUNTER);
        Date startDatetime = visit.getStartDatetime();
        Date stopDatetime = visit.getStopDatetime();

        simpleObject.put("startDatetime", DateFormatUtils.format(startDatetime, "dd MMM yyyy hh:mm a"));

        if (stopDatetime!=null){
            simpleObject.put("stopDatetime", DateFormatUtils.format(stopDatetime, "dd MMM yyyy hh:mm a"));
        } else {
            simpleObject.put("stopDatetime", null);
        }

        List<SimpleObject> encounters = new ArrayList<SimpleObject>();
        simpleObject.put("encounters", encounters);

        String[] datePatterns = { administrationService.getGlobalProperty(UiFrameworkConstants.GP_FORMATTER_DATETIME_FORMAT) };
        for (Encounter e : visit.getEncounters()) {
            if (!e.getVoided()) {
                SimpleObject simpleEncounter = SimpleObject.fromObject(e, uiUtils,  "encounterId", "encounterDatetime", "location", "encounterProviders.provider", "voided", "form");

                Date encounterDatetime = DateUtils.parseDate((String) simpleEncounter.get("encounterDatetime"), datePatterns);
                simpleEncounter.put("encounterDate", DateFormatUtils.format(encounterDatetime, "dd MMM yyyy"));
                simpleEncounter.put("encounterTime", DateFormatUtils.format(encounterDatetime, "hh:mm a"));

                EncounterType encounterType = e.getEncounterType();
                simpleEncounter.put("encounterType", SimpleObject.create("uuid", encounterType.getUuid(), "name", uiUtils.format(encounterType)));
                if(canDelete){
                    simpleEncounter.put("canDelete", true);
                }
                encounters.add(simpleEncounter);
            }
        }

        return simpleObject;
    }


    public SimpleObject getEncounterDetails(@RequestParam("encounterId") Encounter encounter,
                                            UiUtils uiUtils){
        Locale locale = uiUtils.getLocale();

        List<SimpleObject> observations = createObservations(encounter, uiUtils, locale);

        List<SimpleObject> orders = createSimpleObjectWithOrders(encounter, uiUtils);


        return SimpleObject.create("observations", observations, "orders", orders);
    }

    private List<SimpleObject> createObservations(Encounter encounter, UiUtils uiUtils, Locale locale) {
        List<SimpleObject> observations;

        if (encounter.getEncounterType().getUuid().equals(EmrConstants.CONSULTATION_TYPE_UUID)){
            observations = createSimpleObjectWithDiagnosis(encounter, uiUtils, locale);
        } else {
            observations = createSimpleObjectWithObservations(uiUtils, encounter.getObs(), locale);
        }

        return observations;
    }

    private List<SimpleObject> createSimpleObjectWithDiagnosis(Encounter encounter, UiUtils uiUtils, Locale locale) {
        List<SimpleObject> observations = new ArrayList<SimpleObject>();

        Set<Obs> groupedObservations = encounter.getObsAtTopLevel(false);

        for (Obs observation : groupedObservations) {
            SimpleObject simpleObject = SimpleObject.fromObject(observation, uiUtils, "obsId");
            simpleObject.put("question", observation.getConcept().getName(locale).getName());
            simpleObject.put("answer", observation.getValueAsString(locale));
            observations.add(simpleObject);
        }

        return observations;
    }

    private List<SimpleObject> createSimpleObjectWithOrders(Encounter encounter, UiUtils uiUtils) {
        List<SimpleObject> orders = new ArrayList<SimpleObject>();

        for (Order order : encounter.getOrders()) {
            orders.add(SimpleObject.create("concept", uiUtils.format(order.getConcept())));
        }
        return orders;
    }

    private List<SimpleObject> createSimpleObjectWithObservations(UiUtils uiUtils, Set<Obs> obs, Locale locale) {
        List<SimpleObject> encounterDetails = new ArrayList<SimpleObject>();

        for (Obs ob : obs) {

            SimpleObject simpleObject = SimpleObject.fromObject(ob, uiUtils, "obsId");
            simpleObject.put("question", ob.getConcept().getName(locale).getName());
            simpleObject.put("answer", ob.getValueAsString(locale));

            encounterDetails.add(simpleObject);
        }
        return encounterDetails;
    }

    public FragmentActionResult deleteEncounter(UiUtils ui,
                                        @RequestParam("encounterId")Encounter encounter,
                                        @SpringBean("encounterService")EncounterService encounterService,
                                        EmrContext emrContext){

       if(encounter!=null){
           User authenticatedUser = emrContext.getUserContext().getAuthenticatedUser();
           boolean canDelete = authenticatedUser.hasPrivilege(EmrConstants.PRIVILEGE_DELETE_ENCOUNTER);
           if(canDelete){
               encounterService.voidEncounter(encounter, "delete encounter");
               encounterService.saveEncounter(encounter);
           }else{
               return new FailureResult(ui.message("emr.patientDashBoard.deleteEncounter.notAllowed"));
           }
       }
       return new SuccessResult(ui.message("emr.patientDashBoard.deleteEncounter.successMessage"));
    }
}

