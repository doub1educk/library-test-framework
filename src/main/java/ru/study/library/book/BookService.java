package ru.study.library.book;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.library.isbn.InvalidIsbnException;
import ru.study.library.isbn.IsbnClient;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository repository;
    private final IsbnClient isbnClient;

    public BookService(BookRepository repository, IsbnClient isbnClient) {
        this.repository = repository;
        this.isbnClient = isbnClient;
    }

    @Transactional
    public BookResponse create(BookRequest request) {
        checkIsbnCanBeUsed(request.isbn());
        Book book = new Book(request.title(), request.author(), request.isbn(), request.publishedYear());
        return BookResponse.from(repository.save(book));
    }

    public BookResponse getById(long id) {
        return BookResponse.from(findOrThrow(id));
    }

    public List<BookResponse> findAll(String author) {
        List<Book> books = (author == null || author.isBlank())
                ? repository.findAll(Sort.by("id"))
                : repository.findByAuthorContainingIgnoreCaseOrderByIdAsc(author.trim());
        return books.stream().map(BookResponse::from).toList();
    }

    @Transactional
    public BookResponse update(long id, BookRequest request) {
        Book book = findOrThrow(id);
        if (!book.getIsbn().equals(request.isbn())) {
            checkIsbnCanBeUsed(request.isbn());
        }
        book.setTitle(request.title());
        book.setAuthor(request.author());
        book.setIsbn(request.isbn());
        book.setPublishedYear(request.publishedYear());
        return BookResponse.from(book);
    }

    @Transactional
    public void delete(long id) {
        if (!repository.existsById(id)) {
            throw new BookNotFoundException(id);
        }
        repository.deleteById(id);
    }

    /**
     * Порядок проверок важен: сначала дешёвая проверка в своей БД,
     * и только потом обращение к внешнему сервису.
     */
    private void checkIsbnCanBeUsed(String isbn) {
        if (repository.existsByIsbn(isbn)) {
            throw new DuplicateIsbnException(isbn);
        }
        if (!isbnClient.isValid(isbn)) {
            throw new InvalidIsbnException(isbn);
        }
    }

    private Book findOrThrow(long id) {
        return repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }
}
