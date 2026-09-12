package com.uprera.estates.repository;

import com.uprera.estates.dto.ProjectSummary;
import com.uprera.estates.dto.PublishedProjectRef;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Data access for the RERA `projects` and curated `projectcontents` collections.
 *
 * Detail documents are read as raw {@link Document} rather than mapped onto
 * POJOs. That is a deliberate choice: `projectcontents` is defined by
 * schema/project-schema.json, which is expected to grow, and the fields are
 * validated at WRITE time by real_estate_scripts/validate_content.mjs. Typed
 * Java mirrors would add a second definition of every field that could silently
 * disagree with the schema — exactly the drift the master schema exists to
 * prevent. Java does not compute over content; it transports it.
 *
 * The listing projection IS typed (see ProjectSummary) because that shape is
 * small, stable, and drives a UI component.
 */
@Repository
public class ProjectRepository {

    public static final String PROJECTS = "projects";
    public static final String CONTENT = "projectcontents";

    private final MongoTemplate mongo;

    public ProjectRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * A text search must be a {@link TextQuery} with {@code sortByScore()}, not a
     * plain Sort on a "score" field. Mongo exposes relevance only through
     * {@code {$meta: "textScore"}}; sorting on a literal field named "score"
     * silently matches nothing and returns natural collection order.
     */
    private Query buildFilter(String q, String district, String type, Boolean hasDetail) {
        Query query = StringUtils.hasText(q)
                ? TextQuery.queryText(TextCriteria.forDefaultLanguage().matching(q)).sortByScore()
                : new Query();

        // `listed` is the publish gate for the catalogue: it's true only once a
        // project has either its RERA detail page captured (has_detail) or
        // curated content published, and is maintained by the importers, not
        // computed here. Most of the 1,119 scraped projects have neither yet —
        // showing them in the listing would send visitors to a near-empty
        // detail page. This is unconditional, not a query param: there is no
        // "show everything" mode for the public site.
        query.addCriteria(Criteria.where("listed").is(true));

        if (StringUtils.hasText(district)) query.addCriteria(Criteria.where("district").is(district));
        if (StringUtils.hasText(type)) query.addCriteria(Criteria.where("project_type").is(type));
        if (Boolean.TRUE.equals(hasDetail)) query.addCriteria(Criteria.where("has_detail").is(true));
        return query;
    }

    public List<ProjectSummary> search(String q, String district, String type, Boolean hasDetail, int page, int limit) {
        Query query = buildFilter(q, district, type, hasDetail);

        if (StringUtils.hasText(q)) {
            // Relevance ordering comes from TextQuery.sortByScore(); this appends
            // a deterministic tiebreaker. Text scores tie constantly here — a
            // search for "mahagun" produces three projects at 1.375 and several
            // at 1.333 — and without a stable secondary sort, Mongo is free to
            // return tied rows in any order. That makes pagination unsound: the
            // same project can appear on two pages, or on none.
            query.with(Sort.by(Sort.Order.asc("project_name"), Sort.Order.asc("registration_no")));
        } else {
            // Surface projects that actually have captured detail, then
            // alphabetically. registration_no keeps it total.
            query.with(Sort.by(
                    Sort.Order.desc("has_detail"),
                    Sort.Order.asc("project_name"),
                    Sort.Order.asc("registration_no")));
        }

        query.fields()
                .include("registration_no", "project_name", "promoter_name", "district",
                        "project_type", "project_category", "has_detail",
                        "declared_completion_date", "registration_date");

        query.skip((long) (page - 1) * limit).limit(limit);
        return mongo.find(query, ProjectSummary.class, PROJECTS);
    }

    public long count(String q, String district, String type, Boolean hasDetail) {
        Query query = buildFilter(q, district, type, hasDetail);
        return mongo.count(query, PROJECTS);
    }

    public Optional<Document> findByRegistrationNo(String registrationNo) {
        Query query = Query.query(Criteria.where("registration_no").is(registrationNo));
        return Optional.ofNullable(mongo.findOne(query, Document.class, PROJECTS));
    }

    /** Curated brochure content — only ever the published revision. */
    public Optional<Document> findPublishedContent(String registrationNo) {
        Query query = Query.query(Criteria.where("registration_no").is(registrationNo).and("published").is(true));
        return Optional.ofNullable(mongo.findOne(query, Document.class, CONTENT));
    }

    /** Scoped to {@code listed} projects — no point offering a district filter with zero visible results. */
    public List<String> distinctDistricts() {
        Query query = Query.query(Criteria.where("listed").is(true));
        return mongo.findDistinct(query, "district", PROJECTS, String.class)
                .stream()
                .filter(StringUtils::hasText)
                .sorted()
                .toList();
    }

    public long countAll() {
        return mongo.count(new Query(), PROJECTS);
    }

    public long countWithDetail() {
        return mongo.count(Query.query(Criteria.where("has_detail").is(true)), PROJECTS);
    }

    /** How many projects are actually visible in the public listing. */
    public long countListed() {
        return mongo.count(Query.query(Criteria.where("listed").is(true)), PROJECTS);
    }

    /**
     * Registration number + display name of every published project — drives
     * static generation and the frontend's "{@code name-REGISTRATION_NO}" URL
     * slug. {@code marketing.display_name} is a required field for a project to
     * publish (see schema/project-schema.json), so it is expected to always be
     * present here; the registration number is still used as a fallback name so
     * a bad publish can never produce a slug-less URL.
     */
    public List<PublishedProjectRef> publishedProjectRefs() {
        Query query = Query.query(Criteria.where("published").is(true));
        query.fields().include("registration_no").include("marketing.display_name");
        return mongo.find(query, Document.class, CONTENT).stream()
                .filter(d -> StringUtils.hasText(d.getString("registration_no")))
                .map(d -> {
                    String regNo = d.getString("registration_no");
                    Document marketing = d.get("marketing", Document.class);
                    String name = marketing != null ? marketing.getString("display_name") : null;
                    return new PublishedProjectRef(regNo, StringUtils.hasText(name) ? name : regNo);
                })
                .toList();
    }
}
