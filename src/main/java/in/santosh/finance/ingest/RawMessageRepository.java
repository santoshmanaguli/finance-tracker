package in.santosh.finance.ingest;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RawMessageRepository extends JpaRepository<RawMessage, Long> {

    Optional<RawMessage> findByContentHash(String contentHash);
}
