// Non-pthread wasi-sdk libc++ omits std::mutex. libde265 (de265.cc) only uses
// it for a one-time init guard; this single-threaded build never contends, so a
// no-op mutex is correct. The thread pool (threads.h) uses pthread directly and
// links against wasi-libc's single-threaded pthread stubs.
#pragma once
#include <mutex>
namespace std {
#if !defined(_LIBCPP_HAS_THREADS) || _LIBCPP_HAS_THREADS == 0 || defined(_LIBCPP_HAS_NO_THREADS)
class mutex {
public:
  constexpr mutex() noexcept = default;
  mutex(const mutex&) = delete;
  mutex& operator=(const mutex&) = delete;
  void lock() noexcept {}
  bool try_lock() noexcept { return true; }
  void unlock() noexcept {}
};
#endif
}
