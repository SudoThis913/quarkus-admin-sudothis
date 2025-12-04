package com.sudothis.meta;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ModelRegistry {

    private final Map<String, EntityDefinition> registry = new HashMap<>();

    void onStart(@Observes StartupEvent ev, EntityManager em) {
        Set<EntityType<?>> entities = em.getMetamodel().getEntities();

        for (EntityType<?> entity : entities) {
            Class<?> javaType = entity.getJavaType();
            if (javaType.isAnnotationPresent(AdminResource.class)) {
                register(entity);
            }
        }
    }

    private void register(EntityType<?> entity) {
        Class<?> javaType = entity.getJavaType();
        AdminResource annotation = javaType.getAnnotation(AdminResource.class);
        
        String name = javaType.getSimpleName();
        String displayName = annotation.displayName().isEmpty() ? name : annotation.displayName();
        
        List<FieldDefinition> fields = new ArrayList<>();
        
        for (Attribute<?, ?> attr : entity.getAttributes()) {
            boolean required = false;
            if (attr instanceof SingularAttribute) {
                required = !((SingularAttribute<?, ?>) attr).isOptional();
            }
            
            fields.add(new FieldDefinition(
                attr.getName(),
                attr.getJavaType().getSimpleName(),
                required,
                attr.isCollection(),
                attr.isAssociation() ? "TODO_RELATION" : null 
            ));
        }

        EntityDefinition def = new EntityDefinition(
            name,
            displayName,
            "/api/admin/resources/" + name,
            annotation.readOnly(),
            fields
        );

        registry.put(name, def);
        System.out.println("[Meta] Registered Admin Resource: " + name);
    }

    public List<EntityDefinition> getDefinitions() {
        return new ArrayList<>(registry.values());
    }
    
    public EntityDefinition getDefinition(String name) {
        return registry.get(name);
    }
}