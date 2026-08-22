package be.autoservplus.identite.repository;

import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acces aux preuves d acceptation. Table append-only : ce repository sert a
 * ecrire une preuve et a la relire — jamais a la modifier ni a la supprimer.
 */
public interface ConsentementRepository extends JpaRepository<Consentement, Long> {

    List<Consentement> findByUtilisateurEmailIgnoreCaseAndTypeDocument(
            String email, TypeDocumentConsentement typeDocument);
}
