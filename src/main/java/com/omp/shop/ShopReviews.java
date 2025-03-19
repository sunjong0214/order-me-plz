//package com.omp.shop;
//
//import com.omp.review.Review;
//import jakarta.persistence.CascadeType;
//import jakarta.persistence.Embeddable;
//import jakarta.persistence.OneToMany;
//import java.util.List;
//import java.util.concurrent.CopyOnWriteArrayList;
//import lombok.NoArgsConstructor;
//
//@NoArgsConstructor
//@Embeddable
//public class ShopReviews {
//    @OneToMany(cascade = CascadeType.PERSIST, orphanRemoval = true, mappedBy = "shop")
//    private List<Review> reviews = new CopyOnWriteArrayList<>();
//
//    public double addReviewAndCalculateNewAverage(final Review review, final double currentAverage) {
//        int reviewCount = reviews.size();
//        reviews.add(review);
//        return Math.round(newAverage);
//    }
//
//    public double calculateAverageRating() {
//        return Math.round(reviews.stream()
//                .mapToDouble(Review::getRating)
//                .average()
//                .orElse(Double.NaN));
//    }
//
//    public int add(Review review) {
//        reviews.add(review);
//        return reviews.size();
//    }
//
//    public int size() {
//        return reviews.size();
//    }
//}
