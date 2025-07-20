// JavaScript for manual icon swapping on refresh buttons
document.addEventListener('htmx:beforeRequest', function(event) {
  const button = event.target;
  const regularIcon = button.querySelector('#refresh-icon');
  const spinnerIcon = button.querySelector('#refresh-indicator');
  
  if (regularIcon && spinnerIcon) {
    regularIcon.hidden = true;
    spinnerIcon.hidden = false;
    button.disabled = true;
  }
});

document.addEventListener('htmx:afterRequest', function(event) {
  const button = event.target;
  const regularIcon = button.querySelector('#refresh-icon');
  const spinnerIcon = button.querySelector('#refresh-indicator');
  
  if (regularIcon && spinnerIcon) {
    regularIcon.hidden = false;
    spinnerIcon.hidden = true;
    button.disabled = false;
  }
}); 