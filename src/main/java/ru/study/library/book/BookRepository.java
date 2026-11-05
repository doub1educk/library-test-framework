package ru.study.library.book;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);

    List<Book> findByAuthorContainingIgnoreCaseOrderByIdAsc(String author);
}
