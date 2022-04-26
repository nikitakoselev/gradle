package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import spock.lang.Specification

import java.util.stream.Collectors

class WriteDependencyVerificationFileTest extends Specification {

    def "keys should be deduplicated when same keyid is present"() {
        given:
        def keyRings = List.of(
            generateKeyRing(1),
            generateKeyRing(1),
            generateKeyRing(2)
        )

        when:
        def publicKeys = WriteDependencyVerificationFile.gatherPublicKeys(keyRings)

        then:
        publicKeys.size() == 2
        def keyIds = publicKeys.stream()
            .map(PGPPublicKey::getKeyID)
            .distinct()
            .sorted()
            .collect(Collectors.toList())
        keyIds.get(0) == 1
        keyIds.get(1) == 2
    }

    def generateKeyRing(long keyId) {
        PGPPublicKey publicKey = Mock {
            getKeyID() >> keyId
        }
        PGPPublicKeyRing publicKeyRing = Mock {
            getPublicKeys() >> List.of(publicKey).iterator()
        }

        return publicKeyRing
    }

}
