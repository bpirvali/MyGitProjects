package com.bp.bs;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.apache.cxf.jaxrs.ext.xml.ElementClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

@Api( value = "/books", description = "Manage books" )
@Service("bookResourceImpl")
@Path("/books")
public class BookResourceImpl implements BookResource {
	private static final Logger logger = LoggerFactory
			.getLogger(BookResourceImpl.class);

	@Context
	private Request request;
	@Context
	private UriInfo uriInfo;
	
	@GET
	@ApiOperation(value="List matching books", notes="Gets a list of all books matching the search parms!", response = BooksState.class )
	@ApiResponses(value= {@ApiResponse(code=200, message="Books found")})		
	@Produces({"application/json","application/xml"})
	//@ElementClass(response = BooksState.class)
	@ElementClass(response = List.class)
	public Response searchBooks(
			@ApiParam( value = "Search for books containing this keyword", required = false ) 
			@QueryParam("keyword") String keyword, 
			@ApiParam( value = "Search for books with this publication date", required = false )
			@QueryParam("pubdate") String pubDate) {
		logger.trace("Entered searchBooks(...) ");
		List<Book> booksFullObjs = BooksDB.getInstance().searchBooks(keyword, pubDate);
		List<BookState> books = new ArrayList<BookState>();
		
//		BooksState result = new BooksState();
		for (Book book : booksFullObjs) {
			BookState st = new BookState();
			st.setIsbn(book.getIsbn());
			st.setTitle(book.getTitle());
			books.add(st);
		}
		ResponseBuilder builder = Response.ok(books);
		//builder.entity(result.getBook());
		//CacheController.setExpiry(builder);
		return builder.build();
	}
	
	@Valid
	@GET
	@Path("/{isbn}")
	@ApiOperation(value="Get the Book with ISBN", notes="Get the Book with ISBN!", response = BookState.class )
	@ApiResponses(value= {@ApiResponse(code=200, message="Book found"), @ApiResponse(code=404, message="Book not found!")})		
	@Produces({"application/json","application/xml"})
	@ElementClass(response = BookState.class)
	public Response get(
			@ApiParam( value = "Book's ISBN", required = true )
			@PathParam("isbn") String isbn) {
		BooksDB bdb = BooksDB.getInstance();
		Book b = bdb.getBook(isbn);
		if (b != null) {
			logger.info("Book found:" + b);

			BookCacheController cacheController = new BookCacheController(b);

			EntityTag entityTag = new EntityTag(Long.toString(b.getVersion()));
			CacheControl cacheControl = new CacheControl();
			cacheControl.setMaxAge(cacheController.getMaxAge());

			// Eval the entity Tag and last modified date
			ResponseBuilder builder = request.evaluatePreconditions(
					b.getLastModified(), entityTag);
			if (builder == null) {
				BookState bst = createBookState(b);
				builder = Response.ok(bst);
				builder.lastModified(b.getLastModified());
				builder.tag(Long.toString(b.getVersion()));
			}
			builder.cacheControl(cacheControl);
			builder.expires(cacheController.getNextUpdate().getTime());
			return builder.build();
		}
		return Response.status(Status.NOT_FOUND).build();
	}

	@PUT
	@Path("/{isbn}")
	@ApiOperation(value="Update the Book with ISBN", notes="Update the Book with ISBN!", response = Response.class )
	@ApiResponses(value= {@ApiResponse(code=200, message="Book updated!"), @ApiResponse(code=404, message="Book not found!")})			
	@Consumes({"application/json","application/xml"})
	@Produces("application/octet-stream")
	@ElementClass(request = BookState.class)
	public Response update(@PathParam("isbn") String isbn, BookState st) {
		BooksDB bdb = BooksDB.getInstance();
		boolean b = bdb.updateBook(st.getIsbn(), st.getTitle());
		if (b)
			return Response.status(Status.NO_CONTENT).build();
		else
			return Response.status(Status.NOT_FOUND).build();
	}

	@POST
	@ApiOperation(value="Add book", notes="Add book!", response = BookState.class )
	@ApiResponses(value= {@ApiResponse(code=200, message="Book created!")})			
	@Consumes({"application/json","application/xml"})
	@Produces("application/octet-stream")
	@ElementClass(request = BookState.class)
	public Response add(BookState st) {
		BooksDB bdb = BooksDB.getInstance();
		bdb.addBook(new Book(st.getIsbn(), st.getTitle()));
		UriBuilder uriBuilder = uriInfo.getBaseUriBuilder();
		uriBuilder.path(BookResource.class);
		ResponseBuilder builder = Response.created(uriBuilder.build(st
				.getIsbn()));
		return builder.build();
	}

	@DELETE
	@Path("/{isbn}")
	@ApiOperation(value="Delete the Book with ISBN", notes="Delete the Book with ISBN!", response = Response.class )
	@ApiResponses(value= {@ApiResponse(code=200, message="Book deleted!"), @ApiResponse(code=404, message="Book not found!")})			
	@Produces("application/octet-stream")
	public Response delete(@PathParam("isbn") String isbn) {
		BooksDB bdb = BooksDB.getInstance();
		boolean b = bdb.deleteBook(isbn);
		if (b)
			return Response.status(Status.NO_CONTENT).build();
		else
			return Response.status(Status.NOT_FOUND).build();
	}

	@Path("/{isbn}/reviews")
	//@ApiOperation(value="Get the reviews for the Book with ISBN", notes="Get the reviews for the Book with ISBN!", response = ReviewsState.class )
	//@ApiResponses(value= {@ApiResponse(code=200, message="Reviews found!"), @ApiResponse(code=404, message="Book not found!")})			
	@Produces({"application/json","application/xml"})
	public ReviewsResource getReviewsResource(@PathParam("isbn") String isbn) {
		BooksDB bookDB = BooksDB.getInstance();
		Book book = bookDB.getBook(isbn);
		if (book != null) {
			return new ReviewsResource(book);
		} else {
			Response response = Response.status(Status.NOT_FOUND).build();
			throw new WebApplicationException(response);
		}
	}

	private BookState createBookState(Book book) {
		BookState st = new BookState();
		st.setIsbn(book.getIsbn());
		st.setTitle(book.getTitle());
		return st;
	}
}
