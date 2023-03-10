package com.example.fastcampusmysql.domain.post.repository;

import com.example.fastcampusmysql.domain.post.dto.DailyPostCountRequest;
import com.example.fastcampusmysql.domain.post.dto.DailyPostCount;
import com.example.fastcampusmysql.domain.post.entity.Post;
import com.example.fastcampusmysql.util.PageHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class PostRepository {
    static final String TABLE = "Post";
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private static final RowMapper<Post> ROW_MAPPER = (ResultSet resultSet, int rowNum) -> Post.builder()
            .id(resultSet.getLong("id"))
            .memberId(resultSet.getLong("memberId"))
            .contents(resultSet.getString("contents"))
            .createDate(resultSet.getObject("createDate", LocalDate.class))
            .createAt(resultSet.getObject("createAt", LocalDateTime.class))
            .likeCount(resultSet.getLong("likeCount"))
            .version(resultSet.getLong("version"))
            .build();

    public Post save(Post post) {
        if (post.getId() == null) return insert(post);
        return update(post);
    }
    private Post insert(Post post) {
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(namedParameterJdbcTemplate.getJdbcTemplate())
                .withTableName(TABLE)
                .usingGeneratedKeyColumns("id");

        SqlParameterSource params = new BeanPropertySqlParameterSource(post);
        var id = jdbcInsert.executeAndReturnKey(params).longValue();

        return Post.builder()
                .id(id)
                .memberId(post.getMemberId())
                .contents(post.getContents())
                .createDate(post.getCreateDate())
                .createAt(post.getCreateAt())
                .build();
    }

    public void bulkInsert(List<Post> posts) {
        var sql = String.format("""
                INSERT INTO `%s` (memberId, contents, createDate, createAt)
                VALUES (:memberId, :contents, :createDate, :createAt)
                """, TABLE);

        SqlParameterSource[] params = posts
                .stream()
                .map(BeanPropertySqlParameterSource::new)
                .toArray(SqlParameterSource[]::new);
        namedParameterJdbcTemplate.batchUpdate(sql, params);
    }

    public List<DailyPostCount> groupByCreateDate(DailyPostCountRequest request) {
        var params = new BeanPropertySqlParameterSource(request);
        System.out.println(request.firstDate());
        System.out.println(request.lastDate());
        System.out.println(request.memberId());
        String query = String.format("""
                select createAt, memberId , count(*) as postCount
                from %s
                where memberId = :memberId and createAt between :firstDate and :lastDate
                group by createAt , memberId""", TABLE);

        RowMapper<DailyPostCount> row = (rs, rowNum) -> new DailyPostCount(
                rs.getLong("memberId"),
                rs.getObject("createAt", LocalDate.class),
                rs.getLong("postCount")
                );

        return namedParameterJdbcTemplate.query(query, params, row);
    }
    public List<Post> findByMemberId(Long memberId) {
        var params = new MapSqlParameterSource()
                .addValue("memberId", memberId);
        String query = String.format("SELECT * FROM `%s` WHERE id = :id", TABLE);
        return namedParameterJdbcTemplate.query(query, params, ROW_MAPPER);
    }


    public Page<Post> findAllByMemberId(Long memberId, Pageable page) {

        var params = new MapSqlParameterSource()
                .addValue("memberId", memberId)
                .addValue("size", page.getPageSize())
                .addValue("offset", page.getOffset());

        var sql = String.format("select * from post " +
                "where memberId = :memberId " +
                "limit :size " +
                "offset :offset", TABLE, PageHelper.orderBy(page.getSort()));

        var query = namedParameterJdbcTemplate.query(sql, params, ROW_MAPPER);
        var countQuery = getCount(memberId);

        return new PageImpl<>(query, page, countQuery);
    }

    private Integer getCount(Long memberId) {
        String countQuery = String.format("""
                SELECT count(id)
                FROM %s
                WHERE memberId = :memberId
                """, TABLE);
        var countParam = new MapSqlParameterSource().addValue("memberId", memberId);
        return namedParameterJdbcTemplate.queryForObject(countQuery,  countParam, Integer.class);
    }

    public List<Post> findAllByLessThanIdAndMemberIdInAndOrderByIdDesc(Long id, List<Long> memberIds, int size) {
        if (memberIds.isEmpty()) {
            return List.of();
        }

        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("memberIds", memberIds)
                .addValue("size", size);

        String query = String.format("""
                SELECT *
                FROM %s
                WHERE memberId in (:memberIds) and id < :id
                ORDER BY id DESC
                LIMIT :size
                """, TABLE);

        return namedParameterJdbcTemplate.query(query, params, ROW_MAPPER);

    }

    public List<Post> findAllByMemberIdInAndOrderByIdDesc(List<Long> memberIds, int size) {
        if (memberIds.isEmpty()) {
            return List.of();
        }

        var params = new MapSqlParameterSource()
                .addValue("memberIds", memberIds)
                .addValue("size", size);

        String query = String.format("""
                SELECT *
                FROM %s
                WHERE memberId in (:memberIds)
                ORDER BY id DESC
                LIMIT :size
                """, TABLE);

        return namedParameterJdbcTemplate.query(query, params, ROW_MAPPER);

    }

    public List<Post> findAllByLessThanIdAndMemberIdAndOrderByIdDesc(Long id, Long memberId, int size) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("memberId", memberId)
                .addValue("size", size);

        String query = String.format("""
                SELECT *
                FROM %s
                WHERE memberId = :memberId and id < :id
                ORDER BY id DESC
                LIMIT :size
                """, TABLE);

        return namedParameterJdbcTemplate.query(query, params, ROW_MAPPER);
    }

    public List<Post> findAllByMemberIdAndOrderByIdDesc(Long memberId, int size) {
        var params = new MapSqlParameterSource()
                .addValue("memberId", memberId)
                .addValue("size", size);

        String query = String.format("""
                SELECT *
                FROM %s
                WHERE memberId = :memberId
                ORDER BY id DESC
                LIMIT :size
                """, TABLE);

        return namedParameterJdbcTemplate.query(query, params, ROW_MAPPER);
    }

    public List<Post> findAllByIdIn(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return List.of();
        }

        var params = new MapSqlParameterSource()
                .addValue("postIds", postIds);

        String query = String.format("""
                SELECT *
                FROM %s
                WHERE id in (:postIds)
                """, TABLE);

        return namedParameterJdbcTemplate.query(query, params, ROW_MAPPER);

    }

    public Optional<Post> findById(Long postId, boolean requiredLock) {
        String query =String.format("""
                SELECT *
                FROM %s
                WHERE id = :postId
                """, TABLE);
        if (requiredLock) {
            query += "FOR UPDATE";
        }

        var params = new MapSqlParameterSource()
                .addValue("postId", postId);
        var nullablePost = namedParameterJdbcTemplate.queryForObject(query, params, ROW_MAPPER);
        return Optional.ofNullable(nullablePost);
    }

    private Post update(Post post) {
        var sql = String.format("""
        UPDATE %s set 
            memberId = :memberId, 
            contents = :contents, 
            createDate = :createDate, 
            createAt = :createAt, 
            likeCount = :likeCount,
            version = :version + 1 
        WHERE id = :id and version = :version
        """, TABLE);

        SqlParameterSource params = new BeanPropertySqlParameterSource(post);
        var updatedCount = namedParameterJdbcTemplate.update(sql, params);
        if (updatedCount == 0) {
            throw new RuntimeException("not updated");
        }
        return post;
    }


}
