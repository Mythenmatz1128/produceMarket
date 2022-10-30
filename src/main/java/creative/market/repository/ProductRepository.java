package creative.market.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import creative.market.domain.product.Product;
import creative.market.repository.dto.ProductSearchConditionReq;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

import static creative.market.domain.category.QGrade.*;
import static creative.market.domain.category.QGradeCriteria.*;
import static creative.market.domain.category.QItem.*;
import static creative.market.domain.category.QItemCategory.*;
import static creative.market.domain.category.QKind.*;
import static creative.market.domain.category.QKindGrade.*;
import static creative.market.domain.product.QProduct.*;
import static creative.market.domain.user.QUser.*;

@Repository
@RequiredArgsConstructor
public class ProductRepository {

    private final EntityManager em;
    private final JPAQueryFactory queryFactory;

    public Long save(Product product) {
        em.persist(product);
        return product.getId();
    }

    public List<Product> findAll() {
        return queryFactory.selectFrom(product).fetch();
    }

    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(em.find(Product.class, id));
    }

    public List<Product> findProductByCondition(ProductSearchConditionReq condition) { // 조건에 따라 상품 리스트 조회
        return queryFactory.selectFrom(product)
                .join(product.kindGrade, kindGrade)
                .join(kindGrade.kind, kind)
                .join(kind.item, item)
                .join(item.itemCategory, itemCategory)
                .join(item.gradeCriteria, gradeCriteria)
                .where(productNameContains(condition.getProductName()),
                        itemCategoryEq(condition.getItemCategoryCode()),
                        itemCodeEq(condition.getItemCode()),
                        kindEq(condition.getKindId()),
                        kindGradeEq(condition.getKindGradeId()))
                .orderBy(orderCondition(condition.getOrderBy()))
                .fetch();
    }

    public Optional<Product> findByIdFetchJoinSellerAndKind(Long productId) {
        return Optional.ofNullable(
                queryFactory.selectFrom(product)
                        .join(product.kindGrade,kindGrade).fetchJoin()
                        .join(kindGrade.kind, kind).fetchJoin()
                        .join(kindGrade.kind).fetchJoin()
                        .join(product.user, user).fetchJoin()
                        .where(product.id.eq(productId))
                        .fetchOne());
    }

    public Optional<Product> findByIdAndSellerId(Long productId, Long userId) {
        return Optional.ofNullable(
                queryFactory.selectFrom(product)
                        .join(product.user, user)
                        .where(product.id.eq(productId),user.id.eq(userId))
                        .fetchOne()
        );
    }

    public Optional<Product> findByIdFetchJoinSeller(Long productId) {
        return Optional.ofNullable(
                queryFactory.selectFrom(product)
                        .join(product.user, user).fetchJoin()
                        .where(product.id.eq(productId))
                        .fetchOne()
        );
    }

    public Optional<Product> findByIdFetchJoinItemCategory(Long productId) {
        return Optional.ofNullable(
                queryFactory.selectFrom(product)
                        .join(product.kindGrade,kindGrade).fetchJoin()
                        .join(kindGrade.grade, grade)
                        .join(kindGrade.kind, kind).fetchJoin()
                        .join(kind.item,item).fetchJoin()
                        .join(item.itemCategory,itemCategory)
                        .where(product.id.eq(productId))
                        .fetchOne()
        );
    }

    public Double findProductAvgPrice(Long kindGradeId) { // 상품 평균 가격
        return queryFactory.select(product.price.avg())
                .from(product)
                .join(product.kindGrade, kindGrade)
                .where(kindGrade.id.eq(kindGradeId))
                .fetchOne();
    }

    private OrderSpecifier<?> orderCondition(String orderBy) {
        if (orderBy.equals("latest")) { // 최신순
            return product.createdDate.desc();
        } else {//가격순
            return product.price.desc();
        }
    }

    private BooleanExpression kindGradeEq(Long kindGradeId) {
        return kindGradeId != null ? kindGrade.id.eq(kindGradeId) : null;
    }

    private BooleanExpression kindEq(Long kindId) {
        return kindId != null ? kind.id.eq(kindId) : null;
    }

    private BooleanExpression itemCodeEq(Integer itemCode) {
        return itemCode != null ? item.itemCode.eq(itemCode) : null;
    }

    private BooleanExpression itemCategoryEq(Integer itemCategoryCode) {
        return itemCategoryCode != null ? itemCategory.itemCategoryCode.eq(itemCategoryCode) : null;
    }

    private BooleanExpression productNameContains(String productName) {
        return StringUtils.hasText(productName) ? product.name.contains(productName) : null;
    }
}
