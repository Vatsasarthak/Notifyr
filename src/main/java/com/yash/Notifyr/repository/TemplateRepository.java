package com.yash.Notifyr.repository;

import com.yash.Notifyr.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TemplateRepository extends JpaRepository<Template, Long> {

    Optional<Template> findByName(String name);
}
