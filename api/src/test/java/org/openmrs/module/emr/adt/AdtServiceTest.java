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

package org.openmrs.module.emr.adt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.time.DateUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.openmrs.Encounter;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.emr.EmrConstants;
import org.openmrs.module.emr.EmrProperties;
import org.openmrs.module.emr.IsExpectedRequest;
import org.openmrs.module.emr.paperrecord.PaperRecordRequest;
import org.openmrs.module.emr.paperrecord.PaperRecordService;
import org.openmrs.serialization.SerializationException;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.openmrs.module.emr.EmrConstants.UNKNOWN_PATIENT_PERSON_ATTRIBUTE_TYPE_NAME;
import static org.openmrs.module.emr.TestUtils.isCollectionOfExactlyElementsWithProperties;
import static org.openmrs.module.emr.TestUtils.isJustNow;
import static org.openmrs.module.emr.paperrecord.PaperRecordRequest.Status.*;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Context.class)
public class AdtServiceTest {

    private AdtServiceImpl service;

    VisitService mockVisitService;
    PaperRecordService mockPaperRecordService;
    EncounterService mockEncounterService;
    ProviderService mockProviderService;
    PatientService mockPatientService;
    EmrProperties emrProperties;

    private Person personForCurrentUser;
    private Provider providerForCurrentUser;

    private EncounterRole checkInClerkEncounterRole;
    private EncounterType checkInEncounterType;
    private VisitType atFacilityVisitType;
    private LocationTag supportsVisits;
    private Location mirebalaisHospital;
    private Location outpatientDepartment;
    private PersonAttributeType unknownPatientPersonAttributeType;
    private PatientIdentifierType paperRecordIdentifierType;

    @Before
    public void setup() {
        personForCurrentUser = new Person();
        personForCurrentUser.addName(new PersonName("Current", "User", "Person"));

        User authenticatedUser = new User();
        authenticatedUser.setPerson(personForCurrentUser);
        mockStatic(Context.class);
        when(Context.getAuthenticatedUser()).thenReturn(authenticatedUser);

        providerForCurrentUser = new Provider();
        providerForCurrentUser.setPerson(personForCurrentUser);
        mockProviderService = mock(ProviderService.class);
        when(mockProviderService.getProvidersByPerson(personForCurrentUser, false)).thenReturn(Collections.singletonList(providerForCurrentUser));

        mockVisitService = mock(VisitService.class);
        mockEncounterService = mock(EncounterService.class);
        mockPatientService = mock(PatientService.class);
        mockPaperRecordService = mock(PaperRecordService.class);

        checkInClerkEncounterRole = new EncounterRole();
        checkInEncounterType = new EncounterType();
        atFacilityVisitType = new VisitType();

        supportsVisits = new LocationTag();
        supportsVisits.setName(EmrConstants.LOCATION_TAG_SUPPORTS_VISITS);

        outpatientDepartment = new Location();

        mirebalaisHospital = new Location();
        mirebalaisHospital.addTag(supportsVisits);
        mirebalaisHospital.addChildLocation(outpatientDepartment);

        unknownPatientPersonAttributeType = new PersonAttributeType();
        unknownPatientPersonAttributeType.setId(1);
        unknownPatientPersonAttributeType.setPersonAttributeTypeId(10);
        unknownPatientPersonAttributeType.setName(UNKNOWN_PATIENT_PERSON_ATTRIBUTE_TYPE_NAME);
        unknownPatientPersonAttributeType.setFormat("java.lang.String");

        paperRecordIdentifierType = new PatientIdentifierType();

        emrProperties = mock(EmrProperties.class);
        when(emrProperties.getVisitExpireHours()).thenReturn(10);
        when(emrProperties.getCheckInEncounterType()).thenReturn(checkInEncounterType);
        when(emrProperties.getAtFacilityVisitType()).thenReturn(atFacilityVisitType);
        when(emrProperties.getCheckInClerkEncounterRole()).thenReturn(checkInClerkEncounterRole);
        when(emrProperties.getCheckInClerkEncounterRole()).thenReturn(checkInClerkEncounterRole);
        when(emrProperties.getUnknownPatientPersonAttributeType()).thenReturn(unknownPatientPersonAttributeType);
        when(emrProperties.getPaperRecordIdentifierType()).thenReturn(paperRecordIdentifierType);

        AdtServiceImpl service = new AdtServiceImpl();
        service.setPatientService(mockPatientService);
        service.setVisitService(mockVisitService);
        service.setPaperRecordService(mockPaperRecordService);
        service.setEncounterService(mockEncounterService);
        service.setProviderService(mockProviderService);
        service.setEmrProperties(emrProperties);
        this.service = service;
    }


    @Test
    public void testThatRecentVisitIsActive() throws Exception {
        Visit visit = new Visit();
        visit.setStartDatetime(new Date());

        Assert.assertThat(service.isActive(visit), is(true));
    }

    @Test
    public void testThatOldVisitIsNotActive() throws Exception {
        Visit visit = new Visit();
        visit.setStartDatetime(DateUtils.addDays(new Date(), -7));

        Assert.assertThat(service.isActive(visit), is(false));
    }

    @Test
    public void testThatOldVisitWithRecentEncounterIsActive() throws Exception {
        Encounter encounter = new Encounter();
        encounter.setEncounterDatetime(new Date());

        Visit visit = new Visit();
        visit.setStartDatetime(DateUtils.addDays(new Date(), -7));
        visit.addEncounter(encounter);

        Assert.assertThat(service.isActive(visit), is(true));
    }

    @Test
    public void testEnsureActiveVisitCreatesNewVisit() throws Exception {
        final Patient patient = new Patient();

        when(mockVisitService.getVisitsByPatient(patient)).thenReturn(new ArrayList<Visit>());

        service.ensureActiveVisit(patient, outpatientDepartment);

        verify(mockVisitService).saveVisit(argThat(new ArgumentMatcher<Visit>() {
            @Override
            public boolean matches(Object o) {
                Visit actual = (Visit) o;
                assertThat(actual.getVisitType(), is(atFacilityVisitType));
                assertThat(actual.getPatient(), is(patient));
                assertThat(actual.getLocation(), is(mirebalaisHospital));
                assertThat(actual.getStartDatetime(), isJustNow());
                return true;
            }
        }));
    }

    @Test
    public void testEnsureActiveVisitFindsRecentVisit() throws Exception {
        final Patient patient = new Patient();

        Visit recentVisit = new Visit();
        recentVisit.setLocation(mirebalaisHospital);
        recentVisit.setStartDatetime(DateUtils.addHours(new Date(), -1));

        when(mockVisitService.getVisitsByPatient(patient)).thenReturn(Collections.singletonList(recentVisit));

        assertThat(service.ensureActiveVisit(patient, outpatientDepartment), is(recentVisit));

        verify(mockVisitService, times(0)).saveVisit(any(Visit.class));
    }

    @Test
    public void testEnsureActiveVisitDoesNotFindOldVisit() throws Exception {
        final Patient patient = new Patient();

        final Visit oldVisit = new Visit();
        oldVisit.setLocation(mirebalaisHospital);
        oldVisit.setStartDatetime(DateUtils.addDays(new Date(), -7));

        when(mockVisitService.getVisitsByPatient(patient)).thenReturn(Collections.singletonList(oldVisit));

        final Visit created = service.ensureActiveVisit(patient, outpatientDepartment);
        assertNotNull(created);
        assertNotSame(oldVisit, created);

        // should be called once to save oldVisit (having stopped it)
        verify(mockVisitService).saveVisit(argThat(new ArgumentMatcher<Visit>() {
            @Override
            public boolean matches(Object o) {
                Visit actual = (Visit) o;
                if (actual == oldVisit) {
                    // no encounters, so closed at the moment it started
                    assertThat(actual.getStopDatetime(), is(oldVisit.getStartDatetime()));
                    return true;
                } else {
                    return false;
                }
            }
        }));

        // should be called once to create a new visit
        verify(mockVisitService).saveVisit(argThat(new ArgumentMatcher<Visit>() {
            @Override
            public boolean matches(Object o) {
                Visit actual = (Visit) o;
                if (actual != oldVisit) {
                    assertSame(created, actual);
                    assertThat(actual.getVisitType(), is(atFacilityVisitType));
                    assertThat(actual.getPatient(), is(patient));
                    assertThat(actual.getLocation(), is(mirebalaisHospital));
                    assertThat(actual.getStartDatetime(), isJustNow());
                    return true;
                } else {
                    return false;
                }
            }
        }));
    }

    @Test
    public void test_checkInPatient_forNewVisit() throws Exception {
        final Patient patient = new Patient();

        when(mockVisitService.getVisitsByPatient(patient)).thenReturn(new ArrayList<Visit>());

        service.checkInPatient(patient, outpatientDepartment, null, null, null, false);

        verify(mockVisitService).saveVisit(argThat(new ArgumentMatcher<Visit>() {
            @Override
            public boolean matches(Object o) {
                Visit actual = (Visit) o;
                assertThat(actual.getVisitType(), is(atFacilityVisitType));
                assertThat(actual.getPatient(), is(patient));
                assertThat(actual.getLocation(), is(mirebalaisHospital));
                assertThat(actual.getStartDatetime(), isJustNow());
                return true;
            }
        }));

        verify(mockEncounterService).saveEncounter(argThat(new ArgumentMatcher<Encounter>() {
            @Override
            public boolean matches(Object o) {
                Encounter actual = (Encounter) o;
                assertThat(actual.getEncounterType(), is(checkInEncounterType));
                assertThat(actual.getPatient(), is(patient));
                assertThat(actual.getLocation(), is(outpatientDepartment));
                assertThat(actual.getEncounterDatetime(), isJustNow());
                assertThat(actual.getProvidersByRoles().size(), is(1));
                assertThat(actual.getProvidersByRole(checkInClerkEncounterRole).iterator().next(), is(providerForCurrentUser));
                return true;
            }
        }));
    }

	@SuppressWarnings({ "unchecked" })
	@Test
	public void shouldGetAllVisitSummariesOfAllActiveVisit() throws Exception {
		final Visit visit1 = new Visit();
		visit1.setStartDatetime(DateUtils.addHours(new Date(), -2));
		visit1.setLocation(mirebalaisHospital);
		
		final Visit visit2 = new Visit();
		visit2.setStartDatetime(DateUtils.addHours(new Date(), -1));
		visit2.setLocation(outpatientDepartment);
		
		Visit visit3 = new Visit();
		visit3.setStartDatetime(DateUtils.addDays(new Date(), -10));
		visit3.setStopDatetime(DateUtils.addDays(new Date(), -8));
		visit3.setLocation(mirebalaisHospital);

        Set<Location> expectedLocations = new HashSet<Location>();
        expectedLocations.add(mirebalaisHospital);
        expectedLocations.add(outpatientDepartment);

		when(
		    mockVisitService.getVisits(any(Collection.class), any(Collection.class), eq(expectedLocations),
		        any(Collection.class), any(Date.class), any(Date.class), any(Date.class), any(Date.class), any(Map.class),
		        any(Boolean.class), any(Boolean.class))).thenReturn(Arrays.asList(visit1, visit2, visit3));
		
		List<VisitSummary> activeVisitSummaries = service.getActiveVisitSummaries(mirebalaisHospital);

        assertThat(activeVisitSummaries, isCollectionOfExactlyElementsWithProperties("visit", visit1, visit2));
	}

    @Test
    public void shouldCloseInactiveVisitWithLastEncounterDate() {
        Visit visit = new Visit();
        visit.setStartDatetime(DateUtils.addHours(new Date(), -14));

        Encounter encounter1 = new Encounter();
        encounter1.setEncounterDatetime(DateUtils.addHours(new Date(), -14));
        visit.addEncounter(encounter1);

        Date stopDatetime = DateUtils.addHours(new Date(), -14);
        Encounter encounter2 = new Encounter();
        encounter2.setEncounterDatetime(stopDatetime);
        visit.addEncounter(encounter2);

        when(mockVisitService.getVisitsByPatient(null)).thenReturn(Collections.singletonList(visit));

        service.getActiveVisit(null, null);

        assertThat(visit.getStopDatetime(), is(stopDatetime));
    }

    @Test
    public void shouldCloseInactiveVisitWithStartDateIfNoEncounters() {
        Visit visit = new Visit();
        Date startDatetime = DateUtils.addHours(new Date(), -14);
        visit.setStartDatetime(startDatetime);

        when(mockVisitService.getVisitsByPatient(null)).thenReturn(Collections.singletonList(visit));

        service.getActiveVisit(null, null);

        assertThat(visit.getStopDatetime(), is(startDatetime));
    }

    @Test
    public void shouldCloseAnyInactiveButOpenVisits() {
        Visit old1 = new Visit();
        old1.setStartDatetime(DateUtils.addDays(new Date(), -2));

        Encounter oldEncounter = new Encounter();
        oldEncounter.setEncounterDatetime(DateUtils.addHours(DateUtils.addDays(new Date(), -2), 6));

        Visit old2 = new Visit();
        old2.setStartDatetime(DateUtils.addDays(new Date(), -2));
        old2.addEncounter(oldEncounter);

        Visit new1 = new Visit();
        new1.setStartDatetime(DateUtils.addHours(new Date(), -2));

        when(mockVisitService.getVisits(anyCollection(), anyCollection(), anyCollection(), anyCollection(), any(Date.class), any(Date.class), any(Date.class), any(Date.class), anyMap(), anyBoolean(), anyBoolean())).thenReturn(Arrays.asList(old1, old2, new1));

        service.closeInactiveVisits();

        verify(mockVisitService).saveVisit(old1);
        verify(mockVisitService).saveVisit(old2);
        verify(mockVisitService, never()).saveVisit(new1);
        assertNull(new1.getStopDatetime());
        assertNotNull(old1.getStopDatetime());
        assertNotNull(old2.getStopDatetime());
    }

    @Test
    public void testOverlappingVisits() throws Exception {
        Patient patient = new Patient();
        VisitType visitType = new VisitType();

        Date now = new Date();
        Date tenDaysAgo = DateUtils.addDays(now, -10);
        Date nineDaysAgo = DateUtils.addDays(now, -9);
        Date eightDaysAgo = DateUtils.addDays(now, -8);
        Date sevenDaysAgo = DateUtils.addDays(now, -7);

        Visit visit1 = buildVisit(patient, visitType, mirebalaisHospital, tenDaysAgo, eightDaysAgo);
        Visit visit2 = buildVisit(patient, visitType, mirebalaisHospital, now, null);
        Visit visit3 = buildVisit(patient, visitType, null, tenDaysAgo, nineDaysAgo);
        Visit visit4 = buildVisit(patient, visitType, mirebalaisHospital, nineDaysAgo, sevenDaysAgo);

        assertThat(service.visitsOverlap(visit1, visit2), is(false));
        assertThat(service.visitsOverlap(visit1, visit3), is(false));
        assertThat(service.visitsOverlap(visit1, visit4), is(true));
    }

    @Test
    public void testMergePatientsJoinsOverlappingVisits() throws Exception {
        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Date now = new Date();
        Date tenDaysAgo = DateUtils.addDays(now, -10);
        Date nineDaysAgo = DateUtils.addDays(now, -9);
        Date eightDaysAgo = DateUtils.addDays(now, -8);
        Date sevenDaysAgo = DateUtils.addDays(now, -7);

        Visit first = buildVisit(notPreferred, null, mirebalaisHospital, tenDaysAgo, eightDaysAgo);
        Visit last = buildVisit(notPreferred, null, mirebalaisHospital, sevenDaysAgo, null);
        Visit middle = buildVisit(preferred, null, mirebalaisHospital, nineDaysAgo, now);
        Visit unrelated = buildVisit(preferred, null, null, tenDaysAgo, eightDaysAgo);
        Visit voided = buildVisit(notPreferred, null, mirebalaisHospital, tenDaysAgo, sevenDaysAgo);
        voided.setVoided(true);

        first.addEncounter(buildEncounter(notPreferred, tenDaysAgo));
        middle.addEncounter(buildEncounter(preferred, nineDaysAgo));

        when(mockVisitService.getVisitsByPatient(preferred, true, false)).thenReturn(Arrays.asList(middle, unrelated));
        when(mockVisitService.getVisitsByPatient(notPreferred, true, false)).thenReturn(Arrays.asList(first, voided, last));

        service.mergePatients(preferred, notPreferred);

        verify(mockVisitService).voidVisit(eq(first), anyString());
        verify(mockVisitService).voidVisit(eq(last), anyString());

        assertThat(middle.getStartDatetime(), is(tenDaysAgo));
        assertNull(middle.getStopDatetime());
        assertThat(middle.getEncounters().size(), is(2));
        for (Encounter e : middle.getEncounters()) {
            assertThat(e.getVisit(), is(middle));
            assertThat(e.getPatient(), is(middle.getPatient()));
        }

        verify(mockVisitService, times(2)).saveVisit(middle); // two visits merged in

        verify(mockVisitService, never()).saveVisit(unrelated);
        verify(mockVisitService, never()).saveVisit(voided);
        verify(mockVisitService, never()).voidVisit(eq(unrelated), anyString());
        verify(mockVisitService, never()).voidVisit(eq(voided), anyString());

        verify(mockPatientService).mergePatients(preferred, notPreferred);
    }

    @Test
    public void itShouldNotCopyUnknownAttributeWhenMergingAnUnknownPatientIntoAPermanentOne() throws SerializationException {
        Patient preferred = createPatientWithIdAs(10);

        Patient unknownPatient = createPatientWithIdAs(11);

        unknownPatient.addAttribute(new PersonAttribute(unknownPatientPersonAttributeType, "true"));

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                Patient preferred = (Patient) invocationOnMock.getArguments()[0];
                Patient unknownPatient = (Patient) invocationOnMock.getArguments()[1];

                PersonAttribute attribute = unknownPatient.getAttribute(unknownPatientPersonAttributeType);
                preferred.addAttribute(attribute);

                return preferred;
            }
        }).when(mockPatientService).mergePatients(preferred, unknownPatient);

        service.mergePatients(preferred, unknownPatient);

        assertNull(preferred.getAttribute(unknownPatientPersonAttributeType));

        verify(mockPatientService).savePatient(preferred);

    }

    private Patient createPatientWithIdAs(int id) {
        Patient preferred = new Patient();
        preferred.setId(id);
        preferred.setPatientId(id);
        return preferred;
    }

    @Test
    public void testMergePatientsDoesNotResultInOverlappingVisits() throws Exception {
        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Date now = new Date();
        Date twelveDaysAgo = DateUtils.addDays(now, -12);
        Date elevenDaysAgo = DateUtils.addDays(now, -11);
        Date tenDaysAgo = DateUtils.addDays(now, -10);
        Date nineDaysAgo = DateUtils.addDays(now, -9);
        Date eightDaysAgo = DateUtils.addDays(now, -8);
        Date sevenDaysAgo = DateUtils.addDays(now, -7);
        
        //           ___nonPreferredVisit______________
        //           |                                |
        //  |                      |       |                      |
        //  |_firstPreferredVisit__|.......|_secondPreferredVisit_|
        //
        // 12       11            10       9          8           7

        Visit nonPreferredVisit = buildVisit(notPreferred, null, mirebalaisHospital, elevenDaysAgo, eightDaysAgo);
        nonPreferredVisit.addEncounter(buildEncounter(notPreferred, tenDaysAgo));

        Visit firstPreferredVisit =  buildVisit(preferred, null, mirebalaisHospital, twelveDaysAgo, tenDaysAgo);
        firstPreferredVisit.addEncounter(buildEncounter(notPreferred, elevenDaysAgo));

        Visit secondPreferredVisit = buildVisit(preferred, null, mirebalaisHospital, nineDaysAgo, sevenDaysAgo);
        secondPreferredVisit.addEncounter(buildEncounter(notPreferred, eightDaysAgo));

        when(mockVisitService.getVisitsByPatient(notPreferred, true, false)).thenReturn(Arrays.asList(nonPreferredVisit));
        when(mockVisitService.getVisitsByPatient(preferred, true, false)).thenReturn(Arrays.asList(firstPreferredVisit, secondPreferredVisit));
       
        service.mergePatients(preferred, notPreferred);

        assertThat(firstPreferredVisit.getStartDatetime(), is(twelveDaysAgo));
        assertThat(firstPreferredVisit.getStopDatetime(), is(sevenDaysAgo));

        verify(mockVisitService).voidVisit(eq(nonPreferredVisit), anyString());
        verify(mockVisitService).voidVisit(eq(secondPreferredVisit), anyString());
        verify(mockVisitService, times(2)).saveVisit(firstPreferredVisit); // two visits merged in

        verify(mockPatientService).mergePatients(preferred, notPreferred);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowMergingAnUnknownRecordIntoAPermanentOne() {
        Patient preferred = new Patient();
        Patient notPreferred = new Patient();
        preferred.addAttribute(new PersonAttribute(emrProperties.getUnknownPatientPersonAttributeType(), "true"));

        service.mergePatients(preferred, notPreferred);
    }

    @Test
    public void testThatMergingTwoUnknownRecordsResultsInAnUnknownRecord() {
        Patient preferred = new Patient();
        Patient notPreferred = new Patient();
        PersonAttribute preferredIsUnknownAttribute = new PersonAttribute(unknownPatientPersonAttributeType, "true");
        preferred.addAttribute(preferredIsUnknownAttribute);
        notPreferred.addAttribute(new PersonAttribute(emrProperties.getUnknownPatientPersonAttributeType(), "true"));

        service.mergePatients(preferred, notPreferred);

        assertThat(preferred.getAttribute(unknownPatientPersonAttributeType), is(preferredIsUnknownAttribute));
    }

    @Test
    public void testThatMergingTwoPatientsWithMedicalRecordIdentifierAtSameLocationMarksPaperRecordsForMerge() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location someLocation = new Location();
        Location anotherLocation = new Location();

        PatientIdentifier preferredIdentifier =  new PatientIdentifier("123", paperRecordIdentifierType, someLocation);
        PatientIdentifier anotherPreferredIdentifier = new PatientIdentifier("789", paperRecordIdentifierType, anotherLocation); // this is a "fake out" one
        PatientIdentifier notPreferredIdentifier = new PatientIdentifier("456", paperRecordIdentifierType, someLocation);

        preferred.addIdentifier(anotherPreferredIdentifier);
        preferred.addIdentifier(preferredIdentifier);
        notPreferred.addIdentifier(notPreferredIdentifier);

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordsForMerge(preferredIdentifier, notPreferredIdentifier);

        // make sure a merge request is not created for the record at the other location
        verify(mockPaperRecordService, never()).markPaperRecordsForMerge(anotherPreferredIdentifier, notPreferredIdentifier);

    }

    @Test
    public void mergingPatientsShouldCancelOpenRequestForPreferredPatient() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location recordLocation = new Location();
        Location requestLocation = new Location();

        PaperRecordRequest paperRecordRequest = new PaperRecordRequest();
        paperRecordRequest.setIdentifier("ABC");
        paperRecordRequest.setPatient(preferred);
        paperRecordRequest.setRecordLocation(recordLocation);
        paperRecordRequest.setRequestLocation(requestLocation);
        paperRecordRequest.updateStatus(OPEN);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(paperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(paperRecordRequest);

        // TODO: if we ever add back in the "reissue" functionality
        //verify(mockPaperRecordService).requestPaperRecord(preferred, recordLocation, requestLocation);

    }

    @Test
    public void mergingPatientsShouldCancelAssignedToCreateRequestForPreferredPatient() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location recordLocation = new Location();
        Location requestLocation = new Location();

        PaperRecordRequest paperRecordRequest = new PaperRecordRequest();
        paperRecordRequest.setIdentifier("ABC");
        paperRecordRequest.setPatient(preferred);
        paperRecordRequest.setRecordLocation(recordLocation);
        paperRecordRequest.setRequestLocation(requestLocation);
        paperRecordRequest.updateStatus(ASSIGNED_TO_CREATE);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(paperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(paperRecordRequest);

        // TODO: if we ever add back in the "reissue" functionality
        //verify(mockPaperRecordService).requestPaperRecord(preferred, recordLocation, requestLocation);
    }

    @Test
    public void mergingPatientsShouldCancelAssignedToPullRequestForPreferredPatient() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location recordLocation = new Location();
        Location requestLocation = new Location();

        PaperRecordRequest paperRecordRequest = new PaperRecordRequest();
        paperRecordRequest.setIdentifier("ABC");
        paperRecordRequest.setPatient(preferred);
        paperRecordRequest.setRecordLocation(recordLocation);
        paperRecordRequest.setRequestLocation(requestLocation);
        paperRecordRequest.updateStatus(ASSIGNED_TO_PULL);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(paperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(paperRecordRequest);

        // TODO: if we ever add back in the "reissue" functionality
        //verify(mockPaperRecordService).requestPaperRecord(preferred, recordLocation, requestLocation);
    }

    @Test
    public void mergingPatientsShouldNotCancelSentRequestForPreferredPatient() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location recordLocation = new Location();
        Location requestLocation = new Location();

        PaperRecordRequest paperRecordRequest = new PaperRecordRequest();
        paperRecordRequest.setIdentifier("ABC");
        paperRecordRequest.setPatient(preferred);
        paperRecordRequest.setRecordLocation(recordLocation);
        paperRecordRequest.setRequestLocation(requestLocation);
        paperRecordRequest.updateStatus(SENT);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(paperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService, never()).markPaperRecordRequestAsCancelled(paperRecordRequest);

        // TODO: if we ever add back in the "reissue" functionality
        //verify(mockPaperRecordService, never()).requestPaperRecord(preferred, recordLocation, requestLocation);

    }

    @Test
    public void mergingPatientsShouldNotCancelReturnedRequestForPreferredPatient() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location recordLocation = new Location();
        Location requestLocation = new Location();

        PaperRecordRequest paperRecordRequest = new PaperRecordRequest();
        paperRecordRequest.setIdentifier("ABC");
        paperRecordRequest.setPatient(preferred);
        paperRecordRequest.setRecordLocation(recordLocation);
        paperRecordRequest.setRequestLocation(requestLocation);
        paperRecordRequest.updateStatus(RETURNED);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(paperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService, never()).markPaperRecordRequestAsCancelled(paperRecordRequest);

        // TODO: if we ever add back in the "reissue" functionality
        //verify(mockPaperRecordService, never()).requestPaperRecord(preferred, recordLocation, requestLocation);

    }

    @Test
    public void mergingPatientsShouldCancelOpenRequestNotForPreferredPatient() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location recordLocation = new Location();
        Location requestLocation = new Location();

        PaperRecordRequest paperRecordRequest = new PaperRecordRequest();
        paperRecordRequest.setIdentifier("ABC");
        paperRecordRequest.setPatient(notPreferred);
        paperRecordRequest.setRecordLocation(recordLocation);
        paperRecordRequest.setRequestLocation(requestLocation);
        paperRecordRequest.updateStatus(OPEN);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(paperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(paperRecordRequest);

        // TODO: if we ever add back in the "reissue" functionality
        //verify(mockPaperRecordService).requestPaperRecord(preferred, recordLocation, requestLocation);

    }

    @Test
    public void mergingPatientsShouldCancelAssignedToCreateRequestForNotPreferredPatient() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location recordLocation = new Location();
        Location requestLocation = new Location();

        PaperRecordRequest paperRecordRequest = new PaperRecordRequest();
        paperRecordRequest.setIdentifier("ABC");
        paperRecordRequest.setPatient(notPreferred);
        paperRecordRequest.setRecordLocation(recordLocation);
        paperRecordRequest.setRequestLocation(requestLocation);
        paperRecordRequest.updateStatus(ASSIGNED_TO_CREATE);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(paperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(paperRecordRequest);

        // TODO: if we ever add back in the "reissue" functionality
        //verify(mockPaperRecordService).requestPaperRecord(preferred, recordLocation, requestLocation);
    }

    @Test
    public void mergingPatientsShouldCancelAssignedToPullRequestForNotPreferredPatientAndReissue() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location recordLocation = new Location();
        Location requestLocation = new Location();

        PaperRecordRequest paperRecordRequest = new PaperRecordRequest();
        paperRecordRequest.setIdentifier("ABC");
        paperRecordRequest.setPatient(notPreferred);
        paperRecordRequest.setRecordLocation(recordLocation);
        paperRecordRequest.setRequestLocation(requestLocation);
        paperRecordRequest.updateStatus(ASSIGNED_TO_PULL);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(paperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(paperRecordRequest);

        // TODO: if we ever add back in the "reissue" functionality
        //verify(mockPaperRecordService).requestPaperRecord(preferred, recordLocation, requestLocation);
    }

    @Test
    public void mergingPatientsShouldNotCancelSentRequestForNotPreferredPatient() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location recordLocation = new Location();
        Location requestLocation = new Location();

        PaperRecordRequest paperRecordRequest = new PaperRecordRequest();
        paperRecordRequest.setIdentifier("ABC");
        paperRecordRequest.setPatient(notPreferred);
        paperRecordRequest.setRecordLocation(recordLocation);
        paperRecordRequest.setRequestLocation(requestLocation);
        paperRecordRequest.updateStatus(SENT);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(paperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService, never()).markPaperRecordRequestAsCancelled(paperRecordRequest);
        //verify(mockPaperRecordService, never()).requestPaperRecord(preferred, recordLocation, requestLocation);

    }

    @Test
    public void mergingPatientsShouldNotCancelReturnedRequestSendForNotPreferredPatient() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location recordLocation = new Location();
        Location requestLocation = new Location();

        PaperRecordRequest paperRecordRequest = new PaperRecordRequest();
        paperRecordRequest.setIdentifier("ABC");
        paperRecordRequest.setPatient(notPreferred);
        paperRecordRequest.setRecordLocation(recordLocation);
        paperRecordRequest.setRequestLocation(requestLocation);
        paperRecordRequest.updateStatus(RETURNED);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(paperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService, never()).markPaperRecordRequestAsCancelled(paperRecordRequest);

        // TODO: if we ever add back in the "reissue" functionality
        //verify(mockPaperRecordService, never()).requestPaperRecord(preferred, recordLocation, requestLocation);
    }

    @Test
    public void mergingPatientsShouldMoveAllPaperRecordRequestsToPreferredPatient() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location recordLocation = new Location();
        Location requestLocation = new Location();

        PaperRecordRequest preferredRequest = new PaperRecordRequest();
        preferredRequest.setIdentifier("ABC");
        preferredRequest.setPatient(preferred);
        preferredRequest.setRecordLocation(recordLocation);
        preferredRequest.setRequestLocation(requestLocation);
        preferredRequest.updateStatus(SENT);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(preferredRequest));

        PaperRecordRequest nonPreferredRequest = new PaperRecordRequest();
        nonPreferredRequest.setIdentifier("DEF");
        nonPreferredRequest.setPatient(notPreferred);
        nonPreferredRequest.setRecordLocation(recordLocation);
        nonPreferredRequest.setRequestLocation(requestLocation);
        nonPreferredRequest.updateStatus(SENT);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(nonPreferredRequest));

        // we are expecting that the non-preferred request is saved, but that the patient on this request is now the preferred patient
        PaperRecordRequest expectedRequestToSave = new PaperRecordRequest();
        expectedRequestToSave.setIdentifier("DEF");
        expectedRequestToSave.setPatient(preferred);
        expectedRequestToSave.setRecordLocation(recordLocation);
        expectedRequestToSave.setRequestLocation(requestLocation);
        expectedRequestToSave.updateStatus(SENT);

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).savePaperRecordRequest(argThat(new IsExpectedRequest(expectedRequestToSave)));
    }

    // TODO: all these tests are to test improved handling of pending paper record requests when merging two patient records
    // TODO: right now we are just keeping it simple and cancelling and reissuing all requests


   /* @Test
    public void mergingAPreferredPatientWithAnOpenPullChartRequestWithANotPreferredPatientWithAssignedCreateChartRequestWillDropCreateRequest() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location someLocation = new Location();

        PatientIdentifier preferredIdentifier =  new PatientIdentifier("ABC1", paperRecordIdentifierType, someLocation);

        preferred.addIdentifier(preferredIdentifier);

        PaperRecordRequest pullPaperRecordRequest = new PaperRecordRequest();
        pullPaperRecordRequest.setIdentifier("ABC1");
        pullPaperRecordRequest.setPatient(preferred);
        pullPaperRecordRequest.updateStatus(OPEN);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(pullPaperRecordRequest));

        PaperRecordRequest createPaperRecordRequest = new PaperRecordRequest();
        createPaperRecordRequest.setIdentifier("ABC2");
        createPaperRecordRequest.setPatient(notPreferred);
        createPaperRecordRequest.updateStatus(ASSIGNED_TO_CREATE);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(createPaperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(createPaperRecordRequest);

    }


    @Test
    public void mergingAPreferredPatientWithAssignedPullChartRequestWithANotPreferredPatientWithAssignedCreateChartRequestWillDropCreateRequest() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location someLocation = new Location();

        PatientIdentifier preferredIdentifier =  new PatientIdentifier("ABC1", paperRecordIdentifierType, someLocation);

        preferred.addIdentifier(preferredIdentifier);

        PaperRecordRequest pullPaperRecordRequest = new PaperRecordRequest();
        pullPaperRecordRequest.setIdentifier("ABC1");
        pullPaperRecordRequest.setPatient(preferred);
        pullPaperRecordRequest.updateStatus(ASSIGNED_TO_PULL);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(pullPaperRecordRequest));

        PaperRecordRequest createPaperRecordRequest = new PaperRecordRequest();
        createPaperRecordRequest.setIdentifier("ABC2");
        createPaperRecordRequest.setPatient(notPreferred);
        createPaperRecordRequest.updateStatus(ASSIGNED_TO_CREATE);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(createPaperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(createPaperRecordRequest);

    }


    @Test
    public void mergingANotPreferredPatientWithAssignedPullChartRequestWithAPreferredPatientWithAOpenCreateChartRequestWillDropCreateRequest() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location someLocation = new Location();

        PatientIdentifier preferredIdentifier =  new PatientIdentifier("123", paperRecordIdentifierType, someLocation);

        preferred.addIdentifier(preferredIdentifier);

        PaperRecordRequest pullPaperRecordRequest = new PaperRecordRequest();
        pullPaperRecordRequest.setIdentifier("123");
        pullPaperRecordRequest.setPatient(notPreferred);
        pullPaperRecordRequest.updateStatus(ASSIGNED_TO_PULL);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(pullPaperRecordRequest));

        PaperRecordRequest createPaperRecordRequest = new PaperRecordRequest();
        createPaperRecordRequest.setPatient(preferred);
        createPaperRecordRequest.updateStatus(OPEN);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(createPaperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(createPaperRecordRequest);

    }

    @Test
    public void mergingANotPreferredPatientWithOpenPullChartRequestWithAPreferredPatientWithAOpenCreateChartRequestWillDropCreateRequest() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location someLocation = new Location();

        PatientIdentifier preferredIdentifier =  new PatientIdentifier("123", paperRecordIdentifierType, someLocation);

        preferred.addIdentifier(preferredIdentifier);

        PaperRecordRequest pullPaperRecordRequest = new PaperRecordRequest();
        pullPaperRecordRequest.setIdentifier("123");
        pullPaperRecordRequest.setPatient(notPreferred);
        pullPaperRecordRequest.updateStatus(OPEN);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(pullPaperRecordRequest));

        PaperRecordRequest createPaperRecordRequest = new PaperRecordRequest();
        createPaperRecordRequest.setPatient(preferred);
        createPaperRecordRequest.updateStatus(OPEN);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(createPaperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(createPaperRecordRequest);

    }

    @Test
    public void mergingANotPreferredPatientWithAnAssignedPullChartRequestWithAPreferredPatientWithAnAssignedToCreateChartRequestWillDropCreateRequest() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        Location someLocation = new Location();

        PatientIdentifier preferredIdentifier =  new PatientIdentifier("ABC1", paperRecordIdentifierType, someLocation);

        preferred.addIdentifier(preferredIdentifier);

        PaperRecordRequest pullPaperRecordRequest = new PaperRecordRequest();
        pullPaperRecordRequest.setIdentifier("ABC1");
        pullPaperRecordRequest.setPatient(notPreferred);
        pullPaperRecordRequest.updateStatus(ASSIGNED_TO_PULL);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(pullPaperRecordRequest));

        PaperRecordRequest createPaperRecordRequest = new PaperRecordRequest();
        createPaperRecordRequest.setIdentifier("ABC2");
        createPaperRecordRequest.setPatient(preferred);
        createPaperRecordRequest.updateStatus(ASSIGNED_TO_CREATE);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(createPaperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(createPaperRecordRequest);

    }


    @Test
    public void mergingTwoPatientsWithOpenCreatePaperRecordRequestWillLeaveOnlyOneCreateRequest() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        PaperRecordRequest preferredCreatePaperRecordRequest = new PaperRecordRequest();
        preferredCreatePaperRecordRequest.setPatient(preferred);
        preferredCreatePaperRecordRequest.updateStatus(OPEN);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(preferredCreatePaperRecordRequest));

        PaperRecordRequest notPreferredCreatePaperRecordRequest = new PaperRecordRequest();
        notPreferredCreatePaperRecordRequest.setPatient(notPreferred);
        notPreferredCreatePaperRecordRequest.updateStatus(OPEN);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(notPreferredCreatePaperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(notPreferredCreatePaperRecordRequest);
        verify(mockPaperRecordService, never()).markPaperRecordRequestAsCancelled(preferredCreatePaperRecordRequest);
    }

    @Test
    public void mergingPatientWithOpenCreatePaperRecordRequestAndAssignedToCreatePaperRequestCreateWillLeaveOnlyAssignedCreateRequest() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        PaperRecordRequest preferredCreatePaperRecordRequest = new PaperRecordRequest();
        preferredCreatePaperRecordRequest.setPatient(preferred);
        preferredCreatePaperRecordRequest.updateStatus(OPEN);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(preferredCreatePaperRecordRequest));

        PaperRecordRequest notPreferredCreatePaperRecordRequest = new PaperRecordRequest();
        notPreferredCreatePaperRecordRequest.setIdentifier("ABC1");
        notPreferredCreatePaperRecordRequest.setPatient(notPreferred);
        notPreferredCreatePaperRecordRequest.updateStatus(ASSIGNED_TO_CREATE);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(notPreferredCreatePaperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(preferredCreatePaperRecordRequest);
        verify(mockPaperRecordService, never()).markPaperRecordRequestAsCancelled(notPreferredCreatePaperRecordRequest);
    }


    @Test
    public void mergingTwoPatientsWithOpenPullPaperRecordRequestWillLeaveOnlyOnePullRequest() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        PaperRecordRequest preferredPullPaperRecordRequest = new PaperRecordRequest();
        preferredPullPaperRecordRequest.setIdentifier("ABC1");
        preferredPullPaperRecordRequest.setPatient(preferred);
        preferredPullPaperRecordRequest.updateStatus(OPEN);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(preferredPullPaperRecordRequest));

        PaperRecordRequest notPreferredPullPaperRecordRequest = new PaperRecordRequest();
        notPreferredPullPaperRecordRequest.setIdentifier("ABC2");
        notPreferredPullPaperRecordRequest.setPatient(notPreferred);
        notPreferredPullPaperRecordRequest.updateStatus(OPEN);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(notPreferredPullPaperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(notPreferredPullPaperRecordRequest);
        verify(mockPaperRecordService, never()).markPaperRecordRequestAsCancelled(preferredPullPaperRecordRequest);
    }

    @Test
    public void mergingPatientsWithOpenPullPaperRecordRequestWithPatientWithAssignedPullRequestWillLeaveOnlyAssignedPullRequest() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        PaperRecordRequest preferredPullPaperRecordRequest = new PaperRecordRequest();
        preferredPullPaperRecordRequest.setIdentifier("ABC1");
        preferredPullPaperRecordRequest.setPatient(preferred);
        preferredPullPaperRecordRequest.updateStatus(OPEN);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(preferredPullPaperRecordRequest));

        PaperRecordRequest notPreferredPullPaperRecordRequest = new PaperRecordRequest();
        notPreferredPullPaperRecordRequest.setIdentifier("ABC2");
        notPreferredPullPaperRecordRequest.setPatient(notPreferred);
        notPreferredPullPaperRecordRequest.updateStatus(ASSIGNED_TO_PULL);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(notPreferredPullPaperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(preferredPullPaperRecordRequest);
        verify(mockPaperRecordService, never()).markPaperRecordRequestAsCancelled(notPreferredPullPaperRecordRequest);
    }


    @Test
    public void mergingTwoPatientsWithAssignedToPullPaperRecordRequestWillLeaveOnlyOnePullRequest() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        PaperRecordRequest preferredPullPaperRecordRequest = new PaperRecordRequest();
        preferredPullPaperRecordRequest.setIdentifier("ABC1");
        preferredPullPaperRecordRequest.setPatient(preferred);
        preferredPullPaperRecordRequest.updateStatus(ASSIGNED_TO_PULL);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(preferredPullPaperRecordRequest));

        PaperRecordRequest notPreferredPullPaperRecordRequest = new PaperRecordRequest();
        notPreferredPullPaperRecordRequest.setIdentifier("ABC2");
        notPreferredPullPaperRecordRequest.setPatient(notPreferred);
        notPreferredPullPaperRecordRequest.updateStatus(ASSIGNED_TO_PULL);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(notPreferredPullPaperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(notPreferredPullPaperRecordRequest);
        verify(mockPaperRecordService, never()).markPaperRecordRequestAsCancelled(preferredPullPaperRecordRequest);
    }

    @Test
    public void mergingTwoPatientsWithAssignedToCreatePaperRecordRequestWillLeaveOnlyOneCreateRequest() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();

        PaperRecordRequest preferredPullPaperRecordRequest = new PaperRecordRequest();
        preferredPullPaperRecordRequest.setIdentifier("ABC1");
        preferredPullPaperRecordRequest.setPatient(preferred);
        preferredPullPaperRecordRequest.updateStatus(ASSIGNED_TO_CREATE);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(preferredPullPaperRecordRequest));

        PaperRecordRequest notPreferredPullPaperRecordRequest = new PaperRecordRequest();
        notPreferredPullPaperRecordRequest.setIdentifier("ABC2");
        notPreferredPullPaperRecordRequest.setPatient(notPreferred);
        notPreferredPullPaperRecordRequest.updateStatus(ASSIGNED_TO_CREATE);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(notPreferredPullPaperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        verify(mockPaperRecordService).markPaperRecordRequestAsCancelled(notPreferredPullPaperRecordRequest);
        verify(mockPaperRecordService, never()).markPaperRecordRequestAsCancelled(preferredPullPaperRecordRequest);
    }

    @Test
    public void mergingAPatientWithOpenCreatePaperRecordRequestWithAPatientWithPaperRecordIdentifierWillMoveRequestToPull() {

        Patient preferred = new Patient();
        PatientIdentifier identifier = new PatientIdentifier();
        identifier.setIdentifierType(paperRecordIdentifierType);
        identifier.setIdentifier("ABC");
        preferred.addIdentifier(identifier);
        Patient notPreferred = new Patient();

        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.<PaperRecordRequest>emptyList());

        PaperRecordRequest notPreferredCreatePaperRecordRequest = new PaperRecordRequest();
        notPreferredCreatePaperRecordRequest.setPatient(notPreferred);
        notPreferredCreatePaperRecordRequest.updateStatus(OPEN);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(notPreferredCreatePaperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        IsExpectedRequest expectedRequest = new IsExpectedRequest(preferred, OPEN, "ABC");
        verify(mockPaperRecordService, times(1)).savePaperRecordRequest(argThat(expectedRequest));
    }

    @Test
    public void mergingAPatientWithOpenCreatePaperRecordRequestWithANotPreferredPatientWithPaperRecordIdentifierWillMoveRequestToPull() {

        Patient preferred = new Patient();
        Patient notPreferred = new Patient();
        PatientIdentifier identifier = new PatientIdentifier();
        identifier.setIdentifierType(paperRecordIdentifierType);
        identifier.setIdentifier("ABC");
        notPreferred.addIdentifier(identifier);

        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.<PaperRecordRequest>emptyList());

        PaperRecordRequest preferredCreatePaperRecordRequest = new PaperRecordRequest();
        preferredCreatePaperRecordRequest.setPatient(preferred);
        preferredCreatePaperRecordRequest.updateStatus(OPEN);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(preferredCreatePaperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        preferredCreatePaperRecordRequest.setIdentifier("ABC");
        preferredCreatePaperRecordRequest.updateStatus(ASSIGNED_TO_PULL);

        IsExpectedRequest expectedRequest = new IsExpectedRequest(preferred, ASSIGNED_TO_PULL, "ABC");
        verify(mockPaperRecordService, times(1)).savePaperRecordRequest(argThat(expectedRequest));
    }

    @Test
    public void mergingTwoPatientsWithPaperRecordRequestsShouldNotChangeDossierNumberOfRequest() {
        Location someLocation = new Location();

        Patient preferred = new Patient();
        PatientIdentifier preferredIdentifier = new PatientIdentifier("ABC1", paperRecordIdentifierType, someLocation);
        preferred.addIdentifier(preferredIdentifier);

        Patient notPreferred = new Patient();
        PatientIdentifier notPreferredIdentifier = new PatientIdentifier("ABC2", paperRecordIdentifierType, someLocation);
        notPreferred.addIdentifier(notPreferredIdentifier);

        PaperRecordRequest preferredPaperRecordRequest = new PaperRecordRequest();
        preferredPaperRecordRequest.setPatient(preferred);
        preferredPaperRecordRequest.updateStatus(ASSIGNED_TO_CREATE);
        preferredPaperRecordRequest.setIdentifier("ABC1");
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(preferred)).thenReturn(Collections.singletonList(preferredPaperRecordRequest));

        PaperRecordRequest notPreferredPaperRecordRequest = new PaperRecordRequest();
        notPreferredPaperRecordRequest.setPatient(preferred);
        notPreferredPaperRecordRequest.setIdentifier("ABC2");
        notPreferredPaperRecordRequest.updateStatus(ASSIGNED_TO_CREATE);
        when(mockPaperRecordService.getPaperRecordRequestsByPatient(notPreferred)).thenReturn(Collections.singletonList(notPreferredPaperRecordRequest));

        service.mergePatients(preferred, notPreferred);

        IsExpectedRequest expectedNotPreferredRequest = new IsExpectedRequest(preferred,
            ASSIGNED_TO_CREATE, "ABC2");
        verify(mockPaperRecordService, times(1)).savePaperRecordRequest(argThat(expectedNotPreferredRequest));

        verify(mockPaperRecordService).markPaperRecordsForMerge(preferredIdentifier, notPreferredIdentifier);
    }*/

    private Encounter buildEncounter(Patient patient, Date encounterDatetime) {
        Encounter encounter = new Encounter();
        encounter.setPatient(patient);
        encounter.setEncounterDatetime(encounterDatetime);
        return encounter;
    }

    private Visit buildVisit(Patient patient, VisitType visitType, Location location, Date startDate, Date endDate) {
        Visit visit = new Visit();
        visit.setPatient(patient);
        visit.setVisitType(visitType);
        visit.setLocation(location);
        visit.setStartDatetime(startDate);
        visit.setStopDatetime(endDate);
        return visit;
    }



}
