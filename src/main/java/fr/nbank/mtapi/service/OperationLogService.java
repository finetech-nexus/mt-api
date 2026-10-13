package fr.nbank.mtapi.service;

import fr.nbank.mtapi.domain.OperationLog;
import fr.nbank.mtapi.repo.OperationLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {
    private final OperationLogRepository repository;

    public OperationLogService(OperationLogRepository repository) {
        this.repository = repository;
    }

    public void record(String username, String action, String detail) {
        OperationLog row = new OperationLog();
        row.setUsername(username == null || username.isBlank() ? "system" : username);
        row.setAction(action);
        row.setDetail(detail);
        repository.save(row);
    }

    public Page<OperationLog> search(String q, Pageable pageable) {
        Specification<OperationLog> spec = (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + q.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(cb.coalesce(root.get("action"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("detail"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("username"), "")), like));
        };
        return repository.findAll(spec, pageable);
    }
}
