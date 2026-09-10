function Footer() {
    return (
        <footer className="footer">
            <div className="container footer__inner">
                <div className="footer__top">
                    <div className="footer__logo">SIGAC</div>
                    <a href="mailto:sigac@una.ac.cr" className="footer__contact">
                        <span className="footer__contact-icon">✉</span>
                        sigac@una.ac.cr
                    </a>
                </div>
                <div className="footer__bottom">
                    <p className="footer__copy">&copy; 2026 <strong>SIGAC.</strong> Universidad Nacional. Todos los derechos reservados.</p>
                </div>
            </div>
        </footer>
    );
}

export default Footer;
