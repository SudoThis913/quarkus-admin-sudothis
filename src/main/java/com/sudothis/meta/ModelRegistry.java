package com.sudothis.meta;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ModelRegistry {

    private static final Logger LOG = Logger.getLogger(ModelRegistry.class);

    private final Map<String, EntityDefinition> registry = new HashMap<>();
    private final Map<String, Class<?>> classRegistry = new HashMap<>();

    void onStart(@Observes StartupEvent ev, EntityManager em) {
        Set<EntityType<?>> entities = em.getMetamodel().getEntities();

        for (EntityType<?> entity : entities) {
            Class<?> javaType = entity.getJavaType();
            if (javaType.isAnnotationPresent(AdminResource.class)) {
                register(entity, em);
            }
        }
    }

    private void register(EntityType<?> entity, EntityManager em) {
        Class<?> javaType = entity.getJavaType();
        AdminResource annotation = javaType.getAnnotation(AdminResource.class);
        
        String name = javaType.getSimpleName();
        String displayName = annotation.displayName().isEmpty() ? name : annotation.displayName();
        
        List<FieldDefinition> fields = new ArrayList<>();
        Set<String> sortableFields = new HashSet<>();
        // id is always sortable
        sortableFields.add("id");

        for (Attribute<?, ?> attr : entity.getAttributes()) {
            boolean required = false;
            if (attr instanceof SingularAttribute) {
                required = !((SingularAttribute<?, ?>) attr).isOptional();
            }

            String reference = null;
            if (attr.isAssociation()) {
                Class<?> referenceType = null;
                if (attr.isCollection()) {
                    PluralAttribute<?, ?, ?> pa = (PluralAttribute<?, ?, ?>) attr;
                    referenceType = pa.getElementType().getJavaType();
                } else {
                    SingularAttribute<?, ?> sa = (SingularAttribute<?, ?>) attr;
                    referenceType = sa.getJavaType();
                }

                if (referenceType != null && referenceType.isAnnotationPresent(jakarta.persistence.Entity.class)) {
                    reference = referenceType.getSimpleName();
                }
            }

            fields.add(new FieldDefinition(
                attr.getName(),
                attr.getJavaType().getSimpleName(),
                required,
                attr.isCollection(),
                reference
            ));
            LOG.infof("Field: %s, Type: %s, Reference: %s", attr.getName(), attr.getJavaType().getSimpleName(), reference);

            // Only scalar (non-collection, non-association) fields are sortable
            if (!attr.isCollection() && !attr.isAssociation()) {
                sortableFields.add(attr.getName());
            }
        }

        EntityDefinition def = new EntityDefinition(
            name,
            displayName,
            "/api/admin/data/" + name,
            annotation.readOnly(),
            fields,
            Set.copyOf(sortableFields)
        );

        registry.put(name, def);
        classRegistry.put(name, javaType);
        LOG.infof("[Meta] Registered Admin Resource: %s with %d fields", name, fields.size());
    }

    public List<EntityDefinition> getDefinitions() {
        return new ArrayList<>(registry.values());
    }
    
    public EntityDefinition getDefinition(String name) {
        return registry.get(name);
    }

    public Class<?> getEntityClass(String name) {
        return classRegistry.get(name);
    }
}
