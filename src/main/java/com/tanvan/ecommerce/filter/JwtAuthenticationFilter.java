//package com.tanvan.ecommerce.filter;
//
//import com.tanvan.ecommerce.auth.repository.AuthRepository;
//import com.tanvan.ecommerce.utils.JwtUtil;
//import jakarta.servlet.*;
//import jakarta.servlet.http.*;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
//import org.springframework.stereotype.Component;
//import org.springframework.web.filter.OncePerRequestFilter;
//
//import java.io.IOException;
//import java.util.ArrayList;
//
//
//@Component
//public class JwtAuthenticationFilter extends OncePerRequestFilter {
//
//    private final JwtUtil jwtUtil;
//    private final AuthRepository authRepository;
//
//    public JwtAuthenticationFilter(JwtUtil jwtUtil, AuthRepository repo) {
//        this.jwtUtil = jwtUtil;
//        this.authRepository = repo;
//    }
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain filterChain)
//            throws ServletException, IOException {
//
//        String authHeader = request.getHeader("Authorization");
//
//        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//            filterChain.doFilter(request, response);
//            return;
//        }
//
//        String token = authHeader.substring(7);
//        String userId = jwtUtil.extractUserId(token);
//
//        if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
//
//            var user = authRepository.findById(userId).orElse(null);
//
//            if (user != null && jwtUtil.isTokenValid(token)) {
//                var authToken = new UsernamePasswordAuthenticationToken(
//                        user, null, new ArrayList<>()
//                );
//                authToken.setDetails(
//                        new WebAuthenticationDetailsSource().buildDetails(request)
//                );
//                SecurityContextHolder.getContext().setAuthentication(authToken);
//            }
//        }
//        filterChain.doFilter(request, response);
//    }
//}
//
