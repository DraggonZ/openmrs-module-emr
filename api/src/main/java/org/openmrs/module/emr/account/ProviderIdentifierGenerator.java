package org.openmrs.module.emr.account;

import org.openmrs.module.providermanagement.Provider;

/**
 * Implementers can provide implementations of this interface if they wish
 * to automatically create provider identifiers; custom implementations should
 * be injected into the AccountService;
 *
 * When saving an Account, if there is an associated Provider and that Provider
 * does not have an identifier, the generateIdentifier method will be called
 * and the result set as the provider.identifier
 *
 * See MirebalaisProviderIdentifierGenerator for an example implementation; check
 * out the MirebalaisHospitalActivator to see how this generator is set on the
 * AccountService
 */
public interface ProviderIdentifierGenerator {

    String generateIdentifier(Provider provider);

}
