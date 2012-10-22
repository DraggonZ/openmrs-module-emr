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

package org.openmrs.module.emr.api;

import org.apache.commons.lang.time.DateUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.TestOrder;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.emr.EmrConstants;
import org.openmrs.module.emr.api.impl.EmrServiceImpl;
import org.openmrs.module.emr.domain.RadiologyRequisition;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.hasItem;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Context.class)
public class EmrServiceTest {

    EmrService service;

    VisitService mockVisitService;
    EncounterService mockEncounterService;
    AdministrationService mockAdministrationService;
    private OrderType testOrderType;
    private EncounterRole clinicianEncounterRole;
    private EncounterType radiologyOrderEncounterType;
    private EncounterType checkInEncounterType;
    private VisitType unspecifiedVisitType;
    private LocationTag supportsVisits;
    private Location mirebalaisHospital;
    private Location outpatientDepartment;

    @Before
    public void setup() {
        mockVisitService = mock(VisitService.class);
        mockEncounterService = mock(EncounterService.class);

        User authenticatedUser = new User();
        radiologyOrderEncounterType = new EncounterType();
        checkInEncounterType = new EncounterType();
        clinicianEncounterRole = new EncounterRole();
        testOrderType = new OrderType();
        unspecifiedVisitType = new VisitType();

        supportsVisits = new LocationTag();
        supportsVisits.setName(EmrConstants.LOCATION_TAG_SUPPORTS_VISITS);

        mirebalaisHospital = new Location();
        mirebalaisHospital.addTag(supportsVisits);

        outpatientDepartment = new Location();
        outpatientDepartment.setParentLocation(mirebalaisHospital);


        mockStatic(Context.class);
        when(Context.getAuthenticatedUser()).thenReturn(authenticatedUser);

        service = new EmrServiceStub(mockVisitService, mockEncounterService, radiologyOrderEncounterType, checkInEncounterType, clinicianEncounterRole, testOrderType, unspecifiedVisitType);
    }

    @Test
    public void testThatRecentVisitIsActive() throws Exception {
        Visit visit = new Visit();
        visit.setStartDatetime(new Date());

        Assert.assertThat(service.isActive(visit, null), is(true));
    }

    @Test
    public void testThatOldVisitIsNotActive() throws Exception {
        Visit visit = new Visit();
        visit.setStartDatetime(DateUtils.addDays(new Date(), -7));

        Assert.assertThat(service.isActive(visit, null), is(false));
    }

    @Test
    public void testThatOldVisitWithRecentEncounterIsActive() throws Exception {
        Encounter encounter = new Encounter();
        encounter.setEncounterDatetime(new Date());

        Visit visit = new Visit();
        visit.setStartDatetime(DateUtils.addDays(new Date(), -7));
        visit.addEncounter(encounter);

        Assert.assertThat(service.isActive(visit, null), is(true));
    }

    @Test
    public void testEnsureActiveVisitCreatesNewVisit() throws Exception {
        final Patient patient = new Patient();

        when(mockVisitService.getVisitsByPatient(patient)).thenReturn(new ArrayList<Visit>());

        final Date when = new Date();
        service.ensureActiveVisit(patient, outpatientDepartment, when);

        verify(mockVisitService).saveVisit(argThat(new ArgumentMatcher<Visit>() {
            @Override
            public boolean matches(Object o) {
                Visit actual = (Visit) o;
                assertThat(actual.getVisitType(), is(unspecifiedVisitType));
                assertThat(actual.getStartDatetime(), is(when));
                assertThat(actual.getPatient(), is(patient));
                assertThat(actual.getLocation(), is(mirebalaisHospital));
                return true;
            }
        }));
    }

    @Test
    public void testEnsureActiveVisitFindsRecentVisit() throws Exception {
        final Patient patient = new Patient();
        final Date when = new Date();

        Visit recentVisit = new Visit();
        recentVisit.setLocation(mirebalaisHospital);
        recentVisit.setStartDatetime(DateUtils.addHours(when, -1));

        when(mockVisitService.getVisitsByPatient(patient)).thenReturn(Collections.singletonList(recentVisit));

        assertThat(service.ensureActiveVisit(patient, outpatientDepartment, when), is(recentVisit));

        verify(mockVisitService, times(0)).saveVisit(any(Visit.class));
    }

    @Test
    public void testEnsureActiveVisitDoesNotFindOldVisit() throws Exception {
        final Patient patient = new Patient();
        final Date when = new Date();

        final Visit oldVisit = new Visit();
        oldVisit.setLocation(mirebalaisHospital);
        oldVisit.setStartDatetime(DateUtils.addDays(when, -7));

        when(mockVisitService.getVisitsByPatient(patient)).thenReturn(Collections.singletonList(oldVisit));

        final Visit created = service.ensureActiveVisit(patient, outpatientDepartment, when);
        assertNotNull(created);
        assertNotSame(oldVisit, created);

        // should be called once to save oldVisit (having stopped it) and once to create a new visit
        verify(mockVisitService, times(2)).saveVisit(argThat(new ArgumentMatcher<Visit>() {
            @Override
            public boolean matches(Object o) {
                Visit actual = (Visit) o;
                if (actual == oldVisit) {
                    assertNotNull(actual.getStopDatetime());
                    return true;
                } else {
                    assertSame(created, actual);
                    assertThat(actual.getVisitType(), is(unspecifiedVisitType));
                    assertThat(actual.getStartDatetime(), is(when));
                    assertThat(actual.getPatient(), is(patient));
                    assertThat(actual.getLocation(), is(mirebalaisHospital));
                    return true;
                }
            }
        }));
    }

    @Test
    public void test_checkInPatient_forNewVisit() throws Exception {
        final Patient patient = new Patient();
        final Date when = new Date();

        when(mockVisitService.getVisitsByPatient(patient)).thenReturn(new ArrayList<Visit>());

        service.checkInPatient(patient, outpatientDepartment, when, null, null);

        verify(mockVisitService).saveVisit(argThat(new ArgumentMatcher<Visit>() {
            @Override
            public boolean matches(Object o) {
                Visit actual = (Visit) o;
                assertThat(actual.getVisitType(), is(unspecifiedVisitType));
                assertThat(actual.getStartDatetime(), is(when));
                assertThat(actual.getPatient(), is(patient));
                assertThat(actual.getLocation(), is(mirebalaisHospital));
                return true;
            }
        }));

        verify(mockEncounterService).saveEncounter(argThat(new ArgumentMatcher<Encounter>() {
            @Override
            public boolean matches(Object o) {
                Encounter actual = (Encounter) o;
                assertThat(actual.getEncounterType(), is(checkInEncounterType));
                assertThat(actual.getEncounterDatetime(), is(when));
                assertThat(actual.getPatient(), is(patient));
                assertThat(actual.getLocation(), is(outpatientDepartment));
                return true;
            }
        }));
    }

    @Test
    public void testPlaceRadiologyOrders() throws Exception {
        Patient patient = new Patient();
        patient.setPatientId(17);
        Concept armXray = new Concept();
        Location radiologyDepartment = new Location();
        Concept walking = new Concept();
        String patientHistory = "Patient fell off a ladder and may have broken arm";
        Date encounterDatetime = new Date();
        Location encounterLocation = new Location();

        Person drBobPerson = new Person();
        Provider drBob = new Provider();
        drBob.setPerson(drBobPerson);
        User drBobUser = new User();
        drBobUser.setPerson(drBobPerson);

        RadiologyRequisition radiologyRequisition = new RadiologyRequisition();
        radiologyRequisition.setPatient(patient);
        radiologyRequisition.setModality(RadiologyRequisition.Modality.XRAY);
        radiologyRequisition.setRequestedBy(drBob);
        radiologyRequisition.setClinicalHistory(patientHistory);
        radiologyRequisition.setEncounterLocation(encounterLocation);
        radiologyRequisition.setEncounterDatetime(encounterDatetime);
        radiologyRequisition.addStudy(armXray);
        radiologyRequisition.setUrgency(Order.Urgency.STAT);
        radiologyRequisition.setLaterality(TestOrder.Laterality.LEFT);
        //radiologyRequisition.setExamLocation(radiologyDepartment);
        //radiologyRequisition.setTransportation(walking);

        service.placeRadiologyRequisition(radiologyRequisition);

        TestOrder expectedOrder = new TestOrder();
        expectedOrder.setPatient(patient);
        //expectedOrder.setOrderer(drBobUser);
        expectedOrder.setClinicalHistory(patientHistory);
        expectedOrder.setConcept(armXray);
        expectedOrder.setStartDate(encounterDatetime);
        expectedOrder.setOrderType(testOrderType);
        expectedOrder.setUrgency(Order.Urgency.STAT);
        expectedOrder.setLaterality(TestOrder.Laterality.LEFT);

        Encounter expected = new Encounter();
        expected.setEncounterDatetime(encounterDatetime);
        expected.setLocation(encounterLocation);
        expected.setPatient(patient);
        expected.setEncounterType(radiologyOrderEncounterType);
        expected.addOrder(expectedOrder);

        verify(mockEncounterService).saveEncounter(argThat(new IsExpectedEncounter(expected)));
    }

    class IsExpectedEncounter extends ArgumentMatcher<Encounter> {
        private Encounter expected;

        IsExpectedEncounter(Encounter expected) {
            this.expected = expected;
        }

        @Override
        public boolean matches(Object o) {
            Encounter actualEncounter = (Encounter) o;

            assertThat(actualEncounter.getEncounterType(), is(expected.getEncounterType()));
            assertThat(actualEncounter.getPatient(), is(expected.getPatient()));
            assertThat(actualEncounter.getEncounterDatetime(), is(expected.getEncounterDatetime()));
            assertThat(actualEncounter.getLocation(), is(expected.getLocation()));
            assertThat(actualEncounter.getOrders().size(), is(expected.getOrders().size()));

            for (Order order : expected.getOrders()) {
                assertThat(actualEncounter.getOrders(), hasItem(new IsExpectedOrder(order)));
            }

            return true;
        }
    }

    private class EmrServiceStub extends EmrServiceImpl {
        private EncounterType radiologyOrderEncounterType;
        private EncounterType checkInEncounterType;
        private final EncounterRole clinicianEncounterRole;
        private final OrderType testOrderType;
        private VisitType unspecifiedVisitType;

		public EmrServiceStub(VisitService mockVisitService, EncounterService mockEncounterService,
                              EncounterType radiologyOrderEncounterType, EncounterType checkInEncounterType, EncounterRole clinicianEncounterRole, OrderType testOrderType,
                              VisitType unspecifiedVisitType) {

            setVisitService(mockVisitService);
            setEncounterService(mockEncounterService);
            this.radiologyOrderEncounterType = radiologyOrderEncounterType;
            this.checkInEncounterType = checkInEncounterType;
            this.clinicianEncounterRole = clinicianEncounterRole;
            this.testOrderType = testOrderType;
            this.unspecifiedVisitType = unspecifiedVisitType;
        }

        @Override
        protected EncounterType getPlaceOrdersEncounterType() {
            return radiologyOrderEncounterType;
        }

        @Override
        protected EncounterRole getClinicianEncounterRole() {
            return clinicianEncounterRole;
        }

        @Override
        protected OrderType getTestOrderType() {
            return testOrderType;
        }

        @Override
        protected VisitType getUnspecifiedVisitType() {
            return unspecifiedVisitType;
        }

        @Override
        protected EncounterType getCheckInEncounterType() {
            return checkInEncounterType;
        }
    }

    private class IsExpectedOrder extends ArgumentMatcher<Order> {
        private TestOrder expected;

        public IsExpectedOrder(Order expected) {
            this.expected = (TestOrder) expected;
        }

        @Override
        public boolean matches(Object o) {
            TestOrder actual = (TestOrder) o;
            assertThat(actual.getOrderType(), is(expected.getOrderType()));
            assertThat(actual.getPatient(), is(expected.getPatient()));
            assertThat(actual.getConcept(), is(expected.getConcept()));
            assertThat(actual.getInstructions(), is(expected.getInstructions()));
            assertThat(actual.getStartDate(), is(expected.getStartDate()));
            assertThat(actual.getUrgency(), is(expected.getUrgency()));
            assertThat(actual.getClinicalHistory(), is(expected.getClinicalHistory()));
            assertThat(actual.getLaterality(), is(expected.getLaterality()));

            return true;
        }
    }
}
