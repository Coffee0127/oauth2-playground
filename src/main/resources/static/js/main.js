$(function () {
  $('#cleanUp').on('click', () => {
    if (confirm(
      'This will clean up all of your registrations and log out. Are you sure?')) {
      window.location.href = '/api/line/cleanUp';
    }
    return false;
  });
});
